package ru.perevalov.gamerecommenderai.client;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.grpc.*;

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
            throw new RuntimeException("Failed to get recommendations from AI service", e);
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
            throw new RuntimeException("Failed to chat with AI service", e);
        }
    }
}
