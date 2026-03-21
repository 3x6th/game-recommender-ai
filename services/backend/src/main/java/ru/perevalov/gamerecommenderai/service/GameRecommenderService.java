package ru.perevalov.gamerecommenderai.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.filter.RequestIdWebFilter;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.mapper.OwnedGamesSnapshotMapper;
import ru.perevalov.gamerecommenderai.repository.UserGameStatsRepository;
import ru.perevalov.gamerecommenderai.security.UserPrincipalUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

/**
 * Сервис получения рекомендаций игр и формирования AI-контекста.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final GameRecommenderGrpcClient grpcClient;
    private final SteamService steamClient;
    private final UserPrincipalUtil userPrincipalUtil;
    private final AiContextBuilderFactory builderFactory;
    private final ProfileSummaryBuilder profileSummaryBuilder;
    private final UserGameStatsRepository userGameStatsRepository;
    private final OwnedGamesSnapshotMapper ownedGamesSnapshotMapper;

    /**
     * Получает рекомендации с учетом контекста чата.
     *
     * @param request входной запрос на рекомендации
     * @param chatId идентификатор чата для передачи в AI-контекст
     * @return ответ с рекомендациями
     */
    public Mono<GameRecommendationResponse> getGameRecommendationsWithContext(
            GameRecommendationRequest request,
            String chatId
    ) {
        return buildAiContextRequest(request, chatId)
                .flatMap(aiContextRequest -> grpcClient.getGameRecommendations(Mono.just(aiContextRequest)))
                .flatMap(this::processGrpcResponse);
    }

    /**
     * Сохраняет обратную совместимость со старыми вызовами сервиса.
     *
     * @param request поток входного запроса
     * @return ответ с рекомендациями
     */
    public Mono<GameRecommendationResponse> getGameRecommendationsWithContext(Mono<GameRecommendationRequest> request) {
        return request.flatMap(req -> getGameRecommendationsWithContext(req, null));
    }

    /**
     * Формирует AI-контекст из клиентского запроса, профиля игрока и системного request id.
     *
     * @param request входной запрос клиента
     * @param chatId идентификатор чата
     * @return собранный AI-контекст
     */
    private Mono<AiContextRequest> buildAiContextRequest(GameRecommendationRequest request, String chatId) {
        return getProfileSummaryJson(request.getSteamId())
                .defaultIfEmpty("")
                .flatMap(profileSummary -> Mono.deferContextual(ctxView -> {
                    // Серверный request id должен приходить из Reactor Context, если он там уже есть.
                    String serverRequestId = ctxView.getOrDefault(RequestIdWebFilter.REQUEST_ID_CONTEXT_KEY, null);

                    return Mono.just(builderFactory.create()
                            .userMessage(request.getContent())
                            .selectedTags(request.getTags())
                            .profileSummary(profileSummary)
                            .reqId(serverRequestId)
                            .corrId(serverRequestId)
                            .language(null)
                            .maxResults(0)
                            .chatId(chatId)
                            .agentId(null)
                            .excludeGenres(null)
                            .build());
                }));
    }

    /**
     * Формирует JSON со структурированным summary библиотеки пользователя.
     *
     * @param steamIdFromRequest steamId из запроса, если он передан
     * @return JSON-строка с profile summary либо пустой {@link Mono}
     */
    private Mono<String> getProfileSummaryJson(String steamIdFromRequest) {
        return getSteamIdOrEmpty(steamIdFromRequest)
                .onErrorResume(e -> {
                    log.error("Error getting steamId", e);
                    return Mono.empty();
                })
                .flatMap(steamId ->
                        getSnapshotFromDbOrEmpty(steamId)
                                .switchIfEmpty(Mono.defer(() -> getSnapshotFromSteamOrEmpty(steamId)))
                                .flatMap(snapshot -> profileSummaryBuilder.buildJson(snapshot, steamId))
                                .onErrorResume(e -> {
                                    log.error("Error getting profile summary for steamId={}", steamId, e);
                                    return Mono.empty();
                                })
                );
    }

    /**
     * Определяет Steam ID из запроса или security context.
     *
     * @param steamIdFromRequest steamId из запроса
     * @return Steam ID либо пустой {@link Mono}
     */
    private Mono<Long> getSteamIdOrEmpty(String steamIdFromRequest) {
        if (steamIdFromRequest != null && !steamIdFromRequest.isBlank()) {
            return Mono.just(steamIdFromRequest)
                    .map(this::parseSteamId);
        }

        Mono<String> steamIdMono = userPrincipalUtil.getSteamIdFromSecurityContext().defaultIfEmpty("");
        Mono<UserRole> userRoleMono = userPrincipalUtil.getCurrentUserRole().defaultIfEmpty(UserRole.GUEST);

        return Mono.zip(steamIdMono, userRoleMono)
                .flatMap(tuple -> {
                    String contextSteamId = tuple.getT1();
                    UserRole userRole = tuple.getT2();

                    if (userRole.equals(UserRole.GUEST) || contextSteamId.isBlank()) {
                        return Mono.empty();
                    }

                    return Mono.just(contextSteamId);
                })
                .map(this::parseSteamId);
    }

    /**
     * Читает снапшот игр пользователя из базы данных.
     *
     * @param steamId steamId пользователя
     * @return снапшот библиотеки либо пустой {@link Mono}
     */
    private Mono<OwnedGamesSnapshot> getSnapshotFromDbOrEmpty(Long steamId) {
        return userGameStatsRepository.findBySteamId(steamId)
                .mapNotNull(UserGameStats::getOwnedGamesSnapshot)
                .onErrorResume(e -> {
                    log.error("Error retrieving snapshot from database for steamId={}", steamId, e);
                    return Mono.empty();
                });
    }

    /**
     * Загружает снапшот игр пользователя из Steam API как fallback.
     *
     * @param steamId steamId пользователя
     * @return снапшот библиотеки либо пустой {@link Mono}
     */
    private Mono<OwnedGamesSnapshot> getSnapshotFromSteamOrEmpty(Long steamId) {
        return steamClient.getOwnedGames(steamId.toString(), true, true)
                .map(ownedGamesSnapshotMapper::toSnapshot)
                .onErrorResume(e -> {
                    log.error("Error retrieving snapshot from Steam API for steamId={}", steamId, e);
                    return Mono.empty();
                });
    }

    /**
     * Преобразует строковый steamId в число.
     *
     * @param steamId строковое значение steamId
     * @return числовой steamId
     */
    private Long parseSteamId(String steamId) {
        try {
            return Long.parseLong(steamId);
        } catch (NumberFormatException e) {
            throw new GameRecommenderException(ErrorType.INVALID_STEAM_ID_FORMAT, steamId);
        }
    }

    /**
     * Преобразует gRPC-ответ в HTTP DTO ответа сервиса.
     *
     * @param grpcResponse ответ gRPC сервиса
     * @return ответ приложения
     */
    private Mono<GameRecommendationResponse> processGrpcResponse(RecommendationResponse grpcResponse) {
        return Mono.just(grpcResponse)
                .flatMapMany(response -> {
                    if (!response.getSuccess()) {
                        throw new GameRecommenderException(ErrorType.GRPC_AI_ERROR, response.getMessage());
                    }

                    return extractRecommendations(response);
                })
                .collectList()
                .doOnSuccess(gameRecommendations -> {
                    log.info("Received {} recommendations from gRPC service", gameRecommendations.size());

                    if (!gameRecommendations.isEmpty()) {
                        log.info("First recommendation: {}", gameRecommendations.getFirst().getTitle());
                        log.info("Reasoning: {}", grpcResponse.getReasoning());
                    }
                })
                .map(recommendations -> buildResponse(grpcResponse, recommendations)); // ← передаем оба параметра
    }

    /**
     * Извлекает список рекомендаций из gRPC-ответа.
     *
     * @param grpcResponse ответ gRPC сервиса
     * @return поток рекомендаций
     */
    private Flux<ru.perevalov.gamerecommenderai.dto.GameRecommendation> extractRecommendations(
            RecommendationResponse grpcResponse) {
        return Flux.fromIterable(grpcResponse.getRecommendationsList())
                .map(this::mapGrpcToDto);
    }

    /**
     * Строит итоговый ответ приложения из списка рекомендаций.
     *
     * @param recommendations список рекомендаций
     * @return ответ приложения
     */
    private GameRecommendationResponse buildResponse(
            RecommendationResponse grpcResponse,
            List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations) {
        return GameRecommendationResponse.builder()
                .recommendation("Получено " + recommendations.size() + " рекомендаций")
                .reasoning(grpcResponse.getReasoning())
                .success(true)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Преобразует protobuf-модель рекомендации в DTO приложения.
     *
     * @param grpcRec рекомендация из gRPC
     * @return DTO рекомендации
     */
    private ru.perevalov.gamerecommenderai.dto.GameRecommendation mapGrpcToDto(GameRecommendation grpcRec) {
        return ru.perevalov.gamerecommenderai.dto.GameRecommendation.builder()
                .title(grpcRec.getTitle())
                .genre(grpcRec.getGenre())
                .description(grpcRec.getDescription())
                .whyRecommended(grpcRec.getWhyRecommended())
                .platforms(grpcRec.getPlatformsList())
                .rating(grpcRec.getRating())
                .releaseYear(grpcRec.getReleaseYear())
                .build();
    }
}
