package ru.perevalov.gamerecommenderai.client;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.ChatRequest;
import ru.perevalov.gamerecommenderai.grpc.ChatResponse;
import ru.perevalov.gamerecommenderai.grpc.GameRecommenderServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.RecommendationRequest;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;

/**
 * gRPC client for AI service using Spring Boot Starters
 */
@Slf4j
@Component
public class GameRecommenderGrpcClient {

    @GrpcClient("ai-service")
    private GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub gameRecommenderStub;

    /**
     * Get game recommendations
     */
    public RecommendationResponse getRecommendations(String preferences, int maxRecommendations) {
        try {
            log.debug("Sending recommendation request: preferences={}, maxRecommendations={}",
                    preferences, maxRecommendations);

            RecommendationRequest request = RecommendationRequest.newBuilder()
                    .setPreferences(preferences)
                    .setMaxRecommendations(maxRecommendations)
                    .build();

            RecommendationResponse response = gameRecommenderStub.recommend(request);
            log.debug("Received recommendation response: success={}, recommendationsCount={}",
                    response.getSuccess(), response.getRecommendationsCount());

            return response;
        } catch (Exception e) {
            log.error("Error getting recommendations from gRPC service", e);
            throw new GameRecommenderException(ErrorType.AI_SERVICE_RECOMMENDATION_ERROR, preferences);
        }
    }

    /**
     * Chat with AI
     */
    public ChatResponse chatWithAI(String message, String context) {
        try {
            log.debug("Sending chat request: message={}, context={}", message, context);

            ChatRequest request = ChatRequest.newBuilder()
                    .setMessage(message)
                    .setContext(context != null ? context : "")
                    .build();

            ChatResponse response = gameRecommenderStub.chat(request);
            log.debug("Received chat response: success={}", response.getSuccess());

            return response;
        } catch (Exception e) {
            log.error("Error chatting with AI via gRPC service", e);
            throw new GameRecommenderException(ErrorType.CHATTING_WITH_AI_ERROR);
        }
    }
}
