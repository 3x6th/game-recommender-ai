package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.*;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRecommenderService {

    private final GameRecommenderGrpcClient grpcClient;

    public GameRecommendationResponse getGameRecommendation(String preferences) {
        try {
            log.info("Requesting game recommendations via gRPC for preferences: {}", preferences);
            
            RecommendationResponse grpcResponse = grpcClient.getRecommendations(preferences, 5);
            return processGrpcResponse(grpcResponse);
            
        } catch (Exception e) {
            log.error("Error calling gRPC service", e);
            throw new GameRecommenderException(
                "Ошибка при обращении к AI сервису: " + e.getMessage(),
                "GRPC_COMMUNICATION_ERROR",
                500
            );
        }
    }

    private GameRecommendationResponse processGrpcResponse(RecommendationResponse grpcResponse) {
        if (!grpcResponse.getSuccess()) {
            throw new GameRecommenderException(
                "Ошибка от AI сервиса: " + grpcResponse.getMessage(),
                "GRPC_AI_ERROR",
                500
            );
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
            log.error("Error calling gRPC service", e);
            throw new GameRecommenderException(
                "Ошибка при обращении к AI сервису: " + e.getMessage(),
                "GRPC_COMMUNICATION_ERROR",
                500
            );
        }
    }

    private GameRecommendationResponse processChatResponse(ChatResponse grpcResponse) {
        if (!grpcResponse.getSuccess()) {
            throw new GameRecommenderException(
                "Ошибка от AI сервиса: " + grpcResponse.getMessage(),
                "GRPC_AI_ERROR",
                500
            );
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
