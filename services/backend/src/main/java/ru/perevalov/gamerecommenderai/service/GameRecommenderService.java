package ru.perevalov.gamerecommenderai.service;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.AiServiceClient;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.AiRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.GameRecommendation;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.UserPrincipalUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final AiServiceClient aiServiceClient;
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
                .flatMap(aiServiceClient::getGameRecommendations)
                .flatMap(this::processAiResponse);
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

    private Mono<GameRecommendationResponse> processAiResponse(AiRecommendationResponse aiResponse) {
        if (aiResponse == null) {
            return Mono.error(new GameRecommenderException(ErrorType.AI_SERVICE_UNAVAILABLE));
        }

        if (!aiResponse.isSuccess()) {
            return Mono.error(new GameRecommenderException(ErrorType.AI_SERVICE_ERROR, aiResponse.getMessage()));
        }

        List<GameRecommendation> recommendations = aiResponse.getRecommendations() == null
                ? Collections.emptyList()
                : aiResponse.getRecommendations();

        log.info("Received {} recommendations from AI HTTP service", recommendations.size());

        if (!recommendations.isEmpty()) {
            log.info("First recommendation: {}", recommendations.getFirst().getTitle());
        }

        return Mono.just(buildResponse(recommendations));
    }

    private GameRecommendationResponse buildResponse(List<GameRecommendation> recommendations) {
        return GameRecommendationResponse.builder()
                .recommendation("Получено " + recommendations.size() + " рекомендаций")
                .success(true)
                .recommendations(recommendations)
                .build();
    }
}
