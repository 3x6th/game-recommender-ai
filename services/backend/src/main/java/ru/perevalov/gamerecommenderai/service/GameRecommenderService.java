package ru.perevalov.gamerecommenderai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
    private final SteamApiService steamApiService;
    private final RateLimitService rateLimitService;

    @Value("${app.grpc.recommendation.count:5}")
    private int defaultRecommendationCount;

    @Value("${app.grpc.errors.communication.code:GRPC_COMMUNICATION_ERROR}")
    private String grpcCommunicationErrorCode;

    @Value("${app.grpc.errors.ai.code:GRPC_AI_ERROR}")
    private String grpcAiErrorCode;

    @Value("${app.grpc.messages.comm-error-prefix:Ошибка при обращении к AI сервису: }")
    private String grpcCommunicationErrorPrefix;

    @Value("${app.grpc.messages.ai-error-prefix:Ошибка от AI сервиса: }")
    private String grpcAiErrorPrefix;

    @Value("${app.grpc.messages.fallback.recommendations:Сервис временно недоступен. Попробуйте позже.}")
    private String recommendationsFallbackMessage;

    @Value("${app.grpc.messages.fallback.chat:AI сервис временно недоступен. Попробуйте позже.}")
    private String chatFallbackMessage;

    /**
     * Returns game recommendations from AI service via gRPC.
     * Applies caching, retries, timeout and circuit breaker.
     *
     * @param preferences user preferences (genres, tags, etc.)
     * @return response with game recommendations
     * @throws GameRecommenderException when AI service communication fails
     */
    @CircuitBreaker(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "getGameRecommendationFallback")
    @Retry(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "getGameRecommendationFallback")
    @TimeLimiter(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "getGameRecommendationFallback")
    @Cacheable(value = GrpcConstants.CACHE_GAME_RECOMMENDATIONS, key = "#preferences")
    public GameRecommendationResponse getGameRecommendation(String preferences) {
        try {
            log.info("Requesting game recommendations via gRPC for preferences: {}", preferences);
            
            RecommendationResponse grpcResponse = grpcClient.getRecommendations(preferences, defaultRecommendationCount);
            return processGrpcResponse(grpcResponse);
            
        } catch (Exception e) {
            log.error("Error calling gRPC service", e);
            throw new GameRecommenderException(
                grpcCommunicationErrorPrefix + e.getMessage(),
                grpcCommunicationErrorCode,
                500
            );
        }
    }

    /**
     * Fallback для получения рекомендаций при недоступности AI сервиса.
     *
     * @param preferences предпочтения пользователя
     * @param exception причина срабатывания fallback
     * @return базовый ответ с признаком недоступности сервиса
     */
    public GameRecommendationResponse getGameRecommendationFallback(String preferences, Exception exception) {
        log.warn("Fallback triggered for game recommendations, preferences: {}, error: {}", 
                preferences, exception.getMessage());
        
        // Возвращаем базовые рекомендации в случае ошибки
        return GameRecommendationResponse.builder()
                .recommendation(recommendationsFallbackMessage)
                .success(false)
                .build();
    }

    private GameRecommendationResponse processGrpcResponse(RecommendationResponse grpcResponse) {
        if (!grpcResponse.getSuccess()) {
            throw new GameRecommenderException(
                grpcAiErrorPrefix + grpcResponse.getMessage(),
                grpcAiErrorCode,
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

    /**
     * Отправляет сообщение в AI сервис и возвращает ответ.
     * Применяются кэш, ретраи, таймаут и circuit breaker.
     *
     * @param message пользовательское сообщение
     * @return ответ AI сервиса
     * @throws GameRecommenderException при ошибке обращения к AI сервису
     */
    @CircuitBreaker(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "chatWithAIFallback")
    @Retry(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "chatWithAIFallback")
    @TimeLimiter(name = GrpcConstants.GRPC_CLIENT, fallbackMethod = "chatWithAIFallback")
    @Cacheable(value = GrpcConstants.CACHE_USER_PREFERENCES, key = "#message")
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

    /**
     * Fallback для чат-взаимодействия с AI при недоступности сервиса.
     *
     * @param message исходное сообщение пользователя
     * @param exception причина срабатывания fallback
     * @return ответ об ошибке с рекомендацией повторить позже
     */
    public GameRecommendationResponse chatWithAIFallback(String message, Exception exception) {
        log.warn("Fallback triggered for AI chat, message: {}, error: {}", 
                message, exception.getMessage());
        
        return GameRecommendationResponse.builder()
                .recommendation(chatFallbackMessage)
                .success(false)
                .build();
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
