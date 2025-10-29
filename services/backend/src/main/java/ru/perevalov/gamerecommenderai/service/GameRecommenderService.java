package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
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

    public Mono<GameRecommendationResponse> getGameRecommendationsWithContext(Mono<GameRecommendationRequest> request) {
        return request.flatMap(req -> {
                    Mono<SteamOwnedGamesResponse> steamLib = loadSteamLibrary(req.getSteamId());

                    return Mono.just(req)
                            .zipWith(steamLib, (r, lib) ->
                                    AiContextRequest.builder()
                                            .userMessage(r.getContent())
                                            .selectedTags(r.getTags())
                                            .gameLibrary(lib)
                                            .build()
                            );
                })
                .flatMap(aiContextRequest -> grpcClient.getGameRecommendations(Mono.just(aiContextRequest)))
                .flatMap(this::processGrpcResponse)
                .onErrorResume(e -> Mono.error(new GameRecommenderException(ErrorType.STEAM_ID_EXTRACTION_FAILED)));
    }

    /**
     * Загружаем библиотеку стима пользователя из введеннго стим id. Если стим id не введено, пробуем загрузить из
     * секьюрити контекста. Если пользователь не зарегистрирован и не ввел стим id - возвращаем пустой SteamOwnedGamesResponse
     */
    private Mono<SteamOwnedGamesResponse> loadSteamLibrary(String steamId) {
        if (steamId != null && steamId.isBlank()) {
            return steamClient.getOwnedGames(steamId, true, true);
        }

        Mono<String> steamIdMono = userPrincipalUtil.getSteamIdFromSecurityContext();
        Mono<UserRole> userRoleMono = userPrincipalUtil.getCurrentUserRole();

        return Mono.zip(steamIdMono, userRoleMono)
                .flatMap(tuple -> {
                    String contextSteamId = tuple.getT1();
                    UserRole userRole = tuple.getT2();

                    if (userRole.equals(UserRole.GUEST)) {
                        return Mono.just(new SteamOwnedGamesResponse());
                    }

                    return steamClient.getOwnedGames(contextSteamId, true, true);
                });
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