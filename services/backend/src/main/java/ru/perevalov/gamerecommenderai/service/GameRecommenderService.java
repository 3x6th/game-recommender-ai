package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.ChatResponse;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.security.UserPrincipalUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final GameRecommenderGrpcClient grpcClient;
    private final SteamService steamClient;
    private final UserPrincipalUtil userPrincipalUtil;

    /** Старый метод, принимающий запрос от контроллера /proceed. Контроллер был заменен на реактивный. На данный момент
     * обращается к методу - заглушке getRecommendationsReactively.
     * TODO: Написать реактивные сервисные методы, заменить подключение к getRecommendationsReactively на новый реактивный метод
     */

    @Deprecated(forRemoval = true)
    public GameRecommendationResponse getGameRecommendationsWithContext(GameRecommendationRequest request) {
        try {
            SteamOwnedGamesResponse steamLib = loadSteamLibrary(request.getSteamId());

            AiContextRequest context = AiContextRequest.builder()
                    .userMessage(request.getContent())
                    .selectedTags(request.getTags())
                    .gameLibrary(steamLib)
                    .build();

            RecommendationResponse grpcResponse = grpcClient.getGameRecommendations(context);
            return processGrpcResponse(grpcResponse);

        } catch (Exception e) {
            throw new GameRecommenderException(ErrorType.STEAM_ID_EXTRACTION_FAILED);
        }
    }
    //TODO: Удалить метод, когда будет готов реактивный сервисный слой
    public Mono<GameRecommendationResponse> getRecommendationsReactively(Mono<GameRecommendationRequest> request) {
        return request.map(req -> GameRecommendationResponse.builder()
                .recommendation("Default recommendations for request: " + req.getContent())
                .success(true)
                .recommendations(Collections.emptyList())
                .build());
    }

    /** Загружаем библиотеку стима пользователя из введеннго стим id. Если стим id не введено, пробуем загрузить из
     * секьюрити контекста. Если пользователь не зарегистрирован и не ввел стим id - возвращаем пустой SteamOwnedGamesResponse
     */
    public SteamOwnedGamesResponse loadSteamLibrary(String steamId) {
        SteamOwnedGamesResponse steamLibrary = new SteamOwnedGamesResponse();

        if (steamId == null || steamId.isBlank()) {
            steamId = userPrincipalUtil.getSteamIdFromSecurityContext();

            if (userPrincipalUtil.getCurrentUserRole().equals(UserRole.GUEST)) {
                return steamLibrary;
            }
        }

        steamLibrary = steamClient.getOwnedGames(steamId, true,true);
        return steamLibrary;
    }

    public GameRecommendationResponse getGameRecommendation(String preferences) {
        try {
            log.info("Requesting game recommendations via gRPC for preferences: {}", preferences);

            RecommendationResponse grpcResponse = grpcClient.getRecommendations(preferences, 5);
            return processGrpcResponse(grpcResponse);

        } catch (Exception e) {
            log.error("Error calling gRPC service for preferences '{}'. Exception message: '{}'", preferences, e.getMessage(), e);
            throw new GameRecommenderException(ErrorType.GRPC_COMMUNICATION_ERROR);
        }
    }

    private GameRecommendationResponse processGrpcResponse(RecommendationResponse grpcResponse) {
        if (!grpcResponse.getSuccess()) {
            throw new GameRecommenderException(ErrorType.GRPC_AI_ERROR, grpcResponse.getMessage());
        }

        List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations =
                extractRecommendations(grpcResponse);

        logRecommendations(recommendations);

        return buildResponse(recommendations);
    }

    private List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> extractRecommendations(
            RecommendationResponse grpcResponse) {
        return grpcResponse.getRecommendationsList().stream()
                .map(this::mapGrpcToDto)
                .toList();
    }

    private void logRecommendations(List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations) {
        log.info("Received {} recommendations from gRPC service", recommendations.size());
        if (!recommendations.isEmpty()) {
            log.info("First recommendation: {}", recommendations.getFirst().getTitle());
        }
    }

    private GameRecommendationResponse buildResponse(
            List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations) {
        return GameRecommendationResponse.builder()
                .recommendation("Получено " + recommendations.size() + " рекомендаций")
                .success(true)
                .recommendations(recommendations)
                .build();
    }

    public GameRecommendationResponse chatWithAI(String message) {
        try {
            log.info("Sending chat message via gRPC: {}", message);

            ChatResponse grpcResponse = grpcClient.chatWithAI(message, null);
            return processChatResponse(grpcResponse);

        } catch (Exception e) {
            log.error("Error calling gRPC service. Exception message: '{}'", e.getMessage(), e);
            throw new GameRecommenderException(ErrorType.GRPC_COMMUNICATION_ERROR);
        }
    }

    private GameRecommendationResponse processChatResponse(ChatResponse grpcResponse) {
        if (!grpcResponse.getSuccess()) {
            throw new GameRecommenderException(ErrorType.GRPC_AI_ERROR, grpcResponse.getMessage());
        }

        return GameRecommendationResponse.builder()
                .recommendation(grpcResponse.getAiResponse())
                .success(true)
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
