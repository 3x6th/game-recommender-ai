package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.security.UserPrincipalUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final GameRecommenderGrpcClient grpcClient;
    private final SteamService steamClient;
    private final UserPrincipalUtil userPrincipalUtil;
    private final AiContextBuilderFactory builderFactory;

    public Mono<GameRecommendationResponse> getGameRecommendationsWithContext(Mono<GameRecommendationRequest> request) {
        return request.flatMap(req -> {
                    Mono<List<String>> steamLib = loadSteamLibrary(req.getSteamId())
                            .map(s -> Optional.ofNullable(s.getResponse())
                                    .map(SteamOwnedGamesResponse.Response::getGames)
                                    .map(games -> games.stream()
                                            .map(SteamOwnedGamesResponse.Game::getName)
                                            .toList())
                                    .orElse(Collections.emptyList())
                            );

                    return Mono.just(req)
                            .zipWith(steamLib, (r, lib) ->
                                    builderFactory.create()
                                            .userMessage(r.getContent())
                                            .selectedTags(r.getTags())
                                            .profileSummary(lib)
                                            .reqId(null)
                                            .language(null) // TODO: заменить null на реальные значения
                                            .corrId(null)   //       из контекста запроса/пользователя
                                            .maxResults(0)
                                            .chatId(null)
                                            .agentId(null)
                                            .excludeGenres(null)
                                            .build()
                            );
                })
                .flatMap(aiContextRequest -> grpcClient.getGameRecommendations(Mono.just(aiContextRequest)))
                .flatMap(this::processGrpcResponse);
    }

    /**
     * Загружаем библиотеку стима пользователя из введеннго стим id. Если стим id не введено, пробуем загрузить из
     * секьюрити контекста. Если пользователь не зарегистрирован и не ввел стим id - возвращаем пустой SteamOwnedGamesResponse
     */
    private Mono<SteamOwnedGamesResponse> loadSteamLibrary(String steamId) {
        if (steamId != null && !steamId.isBlank()) {
            return steamClient.getOwnedGames(steamId, true, true);
        }

        Mono<String> steamIdMono = userPrincipalUtil.getSteamIdFromSecurityContext().defaultIfEmpty("");
        Mono<UserRole> userRoleMono = userPrincipalUtil.getCurrentUserRole().defaultIfEmpty(UserRole.GUEST);

        return Mono.zip(steamIdMono, userRoleMono)
                .flatMap(tuple -> {
                    String contextSteamId = tuple.getT1();
                    UserRole userRole = tuple.getT2();

                    if (userRole.equals(UserRole.GUEST) || contextSteamId.isBlank()) {
                        return Mono.just(new SteamOwnedGamesResponse());
                    }

                    return steamClient.getOwnedGames(contextSteamId, true, true);
                })
                .switchIfEmpty(Mono.just(new SteamOwnedGamesResponse()));
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
                        log.info("Reasoning: {}", grpcResponse.getReasoning());
                    }
                })
                .map(recommendations -> buildResponse(grpcResponse, recommendations)); // ← передаем оба параметра
    }

    private Flux<ru.perevalov.gamerecommenderai.dto.GameRecommendation> extractRecommendations
            (RecommendationResponse grpcResponse) {
        return Flux.fromIterable(grpcResponse.getRecommendationsList())
                .map(this::mapGrpcToDto);
    }

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