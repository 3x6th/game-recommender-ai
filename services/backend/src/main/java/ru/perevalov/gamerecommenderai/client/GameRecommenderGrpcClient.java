package ru.perevalov.gamerecommenderai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.grpc.*;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

/**
 * gRPC client for AI service using Spring Boot Starters
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameRecommenderGrpcClient {
    private final GrpcMapper mapper;

    @GrpcClient("ai-service")
    private GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub gameRecommenderStub;

    public Mono<RecommendationResponse> getGameRecommendations(Mono<AiContextRequest> request) {
        try {
            AiContextRequest req = request.block();
            log.debug("Sending recommendation request: message={}", req.getUserMessage());

            FullAiContextRequestProto aiContext = mapper.toProto(req);

            RecommendationResponse response = gameRecommenderStub.recommendGames(aiContext);

            log.debug("Received recommendation response: response={}", response.getSuccess());

            // TODO: Переделать в задаче PCAI-82
            return Mono.just(response);
        } catch (Exception e) {
            log.error("Error getting recommendations from gRPC service", e);
            throw new RuntimeException("Failed to get recommendations from AI service", e);
        }
    }
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
