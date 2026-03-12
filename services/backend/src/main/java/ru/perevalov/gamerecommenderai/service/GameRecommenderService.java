package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.mapper.OwnedGamesSnapshotMapper;
import ru.perevalov.gamerecommenderai.repository.UserGameStatsRepository;
import ru.perevalov.gamerecommenderai.security.UserPrincipalUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.List;

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

    public Mono<GameRecommendationResponse> getGameRecommendationsWithContext(Mono<GameRecommendationRequest> request) {
        return request.flatMap(req ->
                        getProfileSummaryJson(req.getSteamId())
                                .defaultIfEmpty("")
                                .map(profileSummary ->
                                        builderFactory.create()
                                                .userMessage(req.getContent())
                                                .selectedTags(req.getTags())
                                                .profileSummary(profileSummary)
                                                .reqId(null)
                                                .language(null) // TODO: заменить null на реальные значения
                                                .corrId(null)   //       из контекста запроса/пользователя
                                                .maxResults(0)
                                                .chatId(null)
                                                .agentId(null)
                                                .excludeGenres(null)
                                                .build()
                                )
                )
                .flatMap(aiContextRequest -> grpcClient.getGameRecommendations(Mono.just(aiContextRequest)))
                .flatMap(this::processGrpcResponse);
    }

    /**
     * Получаем библиотеку пользователя из БД. Если в БД данных нет, то обращаемся к Steam API.
     * Преобразуем библиотеку в Json.
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
     * Если стим id не введено, пробуем загрузить из секьюрити контекста.
     * Если пользователь не зарегистрирован и не ввел стим id - возвращаем пустой Mono
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

    private Mono<OwnedGamesSnapshot> getSnapshotFromDbOrEmpty(Long steamId) {
        return userGameStatsRepository.findBySteamId(steamId)
                .mapNotNull(UserGameStats::getOwnedGamesSnapshot)
                .onErrorResume(e -> {
                    log.error("Error retrieving snapshot from database for steamId={}", steamId, e);
                    return Mono.empty();
                });
    }

    private Mono<OwnedGamesSnapshot> getSnapshotFromSteamOrEmpty(Long steamId) {
        return steamClient.getOwnedGames(steamId.toString(), true, true)
                .map(ownedGamesSnapshotMapper::toSnapshot)
                .onErrorResume(e -> {
                    log.error("Error retrieving snapshot from Steam API for steamId={}", steamId, e);
                    return Mono.empty();
                });
    }

    private Long parseSteamId(String steamId) {
        try {
            return Long.parseLong(steamId);
        } catch (NumberFormatException e) {
            throw new GameRecommenderException(ErrorType.INVALID_STEAM_ID_FORMAT, steamId);
        }
    }

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
                    }
                })
                .map(this::buildResponse);
    }

    private Flux<ru.perevalov.gamerecommenderai.dto.GameRecommendation> extractRecommendations
            (RecommendationResponse grpcResponse) {
        return Flux.fromIterable(grpcResponse.getRecommendationsList())
                .map(this::mapGrpcToDto);
    }

    private GameRecommendationResponse buildResponse(
            List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations) {
        return GameRecommendationResponse.builder()
                .recommendation("Получено " + recommendations.size() + " рекомендаций")
                .success(true)
                .recommendations(recommendations)
                .build();
    }

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