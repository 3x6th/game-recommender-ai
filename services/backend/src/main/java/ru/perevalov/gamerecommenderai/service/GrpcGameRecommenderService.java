package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcGameRecommenderService {

    private final GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub gameRecommenderStub;

    public GameRecommendationResponse getGameRecommendation(String preferences) {
        try {
            log.info("Requesting game recommendations via gRPC for preferences: {}", preferences);
            
            RecommendationRequest request = RecommendationRequest.newBuilder()
                    .setPreferences(preferences)
                    .setMaxRecommendations(5)
                    .build();

            RecommendationResponse grpcResponse = gameRecommenderStub.recommend(request);
            
            if (grpcResponse.getSuccess()) {
                List<ru.perevalov.gamerecommenderai.dto.GameRecommendation> recommendations = 
                    grpcResponse.getRecommendationsList().stream()
                        .map(this::mapGrpcToDto)
                        .toList();

                log.info("Received {} recommendations from gRPC service", recommendations.size());
                if (!recommendations.isEmpty()) {
                    log.info("First recommendation: {}", recommendations.getFirst().getTitle());
                }

                return GameRecommendationResponse.builder()
                        .recommendation("Получено " + recommendations.size() + " рекомендаций")
                        .success(true)
                        .recommendations(recommendations)
                        .build();
            } else {
                throw new GameRecommenderException(
                    "Ошибка от AI сервиса: " + grpcResponse.getMessage(),
                    "GRPC_AI_ERROR",
                    500
                );
            }
        } catch (Exception e) {
            log.error("Error calling gRPC service", e);
            throw new GameRecommenderException(
                "Ошибка при обращении к AI сервису: " + e.getMessage(),
                "GRPC_COMMUNICATION_ERROR",
                500
            );
        }
    }

    public GameRecommendationResponse chatWithAI(String message) {
        try {
            log.info("Sending chat message via gRPC: {}", message);
            
            ChatRequest request = ChatRequest.newBuilder()
                    .setMessage(message)
                    .build();

            ChatResponse grpcResponse = gameRecommenderStub.chat(request);
            
            if (grpcResponse.getSuccess()) {
                return GameRecommendationResponse.builder()
                        .recommendation(grpcResponse.getAiResponse())
                        .success(true)
                        .build();
            } else {
                throw new GameRecommenderException(
                    "Ошибка от AI сервиса: " + grpcResponse.getMessage(),
                    "GRPC_AI_ERROR",
                    500
                );
            }
        } catch (Exception e) {
            log.error("Error calling gRPC service", e);
            throw new GameRecommenderException(
                "Ошибка при обращении к AI сервису: " + e.getMessage(),
                "GRPC_COMMUNICATION_ERROR",
                500
            );
        }
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
