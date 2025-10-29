package ru.perevalov.gamerecommenderai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.grpc.ChatRequest;
import ru.perevalov.gamerecommenderai.grpc.ChatResponse;
import ru.perevalov.gamerecommenderai.grpc.ReactorGameRecommenderServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

/**
 * gRPC client for AI service using Spring Boot Starters
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameRecommenderGrpcClient {

    private final GrpcMapper mapper;

    // Реактивная stub вместо блокирующей
    @GrpcClient("ai-service")
    private ReactorGameRecommenderServiceGrpc.ReactorGameRecommenderServiceStub gameRecommenderServiceStub;

    /**
     * Реактивная версия получения game-recommendations
     */
    public Mono<RecommendationResponse> getGameRecommendations(Mono<AiContextRequest> aiContextRequest) {
        return aiContextRequest.doOnNext(req -> log.info(
                                       "Sending recommendation request: message={}",
                                       req.getUserMessage()
                               ))
                               .map(mapper::toProto)
                               .flatMap(gameRecommenderServiceStub::recommendGames)
                               .doOnSuccess(response -> log.info(
                                       "Received recommendation response: response={}",
                                       response.getSuccess()
                               ))
                               .doOnError(error -> log.error(
                                       "Error getting recommendations from gRPC service",
                                       error
                               ))
                               .onErrorMap(error -> new RuntimeException(
                                       "Failed to get recommendations from AI service",
                                       error
                               ));
    }

    /**
     * Chat with AI реактивная версия
     */
    public Mono<ChatResponse> chatWithAI(String message, String context) {
        log.info(
                "Sending chat request: message={}, context={}",
                message,
                context
        );
        return Mono.fromCallable(() -> ChatRequest.newBuilder()
                                                  .setMessage(message)
                                                  .setContext(context != null ? context : "")
                                                  .build())
                   .flatMap(gameRecommenderServiceStub::chat)
                   .doOnSuccess(response -> log.info(
                           "Received chat response: success={}",
                           response.getSuccess()
                   ))
                   .doOnError(error -> log.error("Error chatting with AI via gRPC service", error))
                   .onErrorMap(error -> new RuntimeException(
                           "Failed to chat with AI service",
                           error
                   ));
    }

}
