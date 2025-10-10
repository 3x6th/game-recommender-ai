package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final GameRecommenderGrpcClient grpcClient;
    private final SteamService steamClient;

    public GameRecommendationResponse getGameRecommendationsWithContext(GameRecommendationRequest request) {
        try {
            SteamOwnedGamesResponse steamLib = new SteamOwnedGamesResponse();

            if (request.getSteamId() != null && !request.getSteamId().isBlank()) {
                steamLib = steamClient.getOwnedGames(
                        request.getSteamId(),
                        true,
                        true
                );
            }

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
