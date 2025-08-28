package ru.perevalov.gamerecommenderai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamApiService {

    private final WebClient.Builder webClientBuilder;
    private final RateLimitService rateLimitService;
    
    @Value("${app.steam.api.base-url}")
    private String steamApiBaseUrl;
    
    @Value("${app.steam.api.timeout}")
    private Duration timeout;

    /**
     * Получает подробную информацию об игре из Steam API.
     * Применяются rate limit, кэширование, ретраи, таймауты и circuit breaker.
     *
     * @param gameId идентификатор игры (Steam appid)
     * @param userId идентификатор пользователя (для rate limiting)
     * @return реактивный Mono с картой результата
     */
    @CircuitBreaker(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Retry(name = "steamApi", fallbackMethod = "steamApiFallback")
    @TimeLimiter(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Cacheable(value = "steamData", key = "#gameId")
    public Mono<Map<String, Object>> getGameDetails(String gameId, String userId) {
        if (rateLimitService.isRateLimited(userId)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            return Mono.error(new RuntimeException("Rate limit exceeded"));
        }

        log.info("Fetching game details from Steam API for game: {}, user: {}", gameId, userId);
        
        return webClientBuilder.build()
                .get()
                .uri(steamApiBaseUrl + "/appdetails?appids=" + gameId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(result -> (Map<String, Object>) result)
                .timeout(timeout)
                .doOnSuccess(result -> log.info("Successfully fetched game details for game: {}", gameId))
                .doOnError(error -> log.error("Error fetching game details for game: {}", gameId, error));
    }

    /**
     * Возвращает список игр пользователя из Steam API.
     * Применяются rate limit, кэширование, ретраи, таймауты и circuit breaker.
     *
     * @param userId идентификатор пользователя Steam
     * @return реактивный Mono с картой результата
     */
    @CircuitBreaker(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Retry(name = "steamApi", fallbackMethod = "steamApiFallback")
    @TimeLimiter(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Cacheable(value = "steamData", key = "'user_games_' + #userId")
    public Mono<Map<String, Object>> getUserGames(String userId) {
        if (rateLimitService.isRateLimited(userId)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            return Mono.error(new RuntimeException("Rate limit exceeded"));
        }

        log.info("Fetching user games from Steam API for user: {}", userId);
        
        return webClientBuilder.build()
                .get()
                .uri(steamApiBaseUrl + "/IPlayerService/GetOwnedGames/v1/?steamid=" + userId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(result -> (Map<String, Object>) result)
                .timeout(timeout)
                .doOnSuccess(result -> log.info("Successfully fetched user games for user: {}", userId))
                .doOnError(error -> log.error("Error fetching user games for user: {}", userId, error));
    }

    /**
     * Получает рекомендации к игре из Steam API.
     * Применяются rate limit, кэширование, ретраи, таймауты и circuit breaker.
     *
     * @param gameId идентификатор игры (Steam appid)
     * @param userId идентификатор пользователя (для rate limiting)
     * @return реактивный Mono с картой результата
     */
    @CircuitBreaker(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Retry(name = "steamApi", fallbackMethod = "steamApiFallback")
    @TimeLimiter(name = "steamApi", fallbackMethod = "steamApiFallback")
    @Cacheable(value = "steamData", key = "'game_recommendations_' + #gameId")
    public Mono<Map<String, Object>> getGameRecommendations(String gameId, String userId) {
        if (rateLimitService.isRateLimited(userId)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            return Mono.error(new RuntimeException("Rate limit exceeded"));
        }

        log.info("Fetching game recommendations from Steam API for game: {}, user: {}", gameId, userId);
        
        return webClientBuilder.build()
                .get()
                .uri(steamApiBaseUrl + "/recommendations?appid=" + gameId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(result -> (Map<String, Object>) result)
                .timeout(timeout)
                .doOnSuccess(result -> log.info("Successfully fetched recommendations for game: {}", gameId))
                .doOnError(error -> log.error("Error fetching recommendations for game: {}", gameId, error));
    }

    /**
     * Fallback для методов Steam API при ошибках/высокой задержке.
     *
     * @param identifier идентификатор запроса (gameId/userId)
     * @param userId идентификатор пользователя (если применимо)
     * @param exception причина срабатывания fallback
     * @return Mono с признаком fallback и сообщением об ошибке
     */
    public Mono<Map<String, Object>> steamApiFallback(String identifier, String userId, Exception exception) {
        log.warn("Steam API fallback triggered for identifier: {}, user: {}, error: {}", 
                identifier, userId, exception.getMessage());
        
        // Возвращаем пустой результат в случае ошибки
        return Mono.just(Map.of("error", "Service temporarily unavailable", "fallback", true));
    }

    /**
     * Перегруженный fallback без userId.
     *
     * @param identifier идентификатор запроса
     * @param exception причина срабатывания fallback
     * @return Mono с признаком fallback и сообщением об ошибке
     */
    public Mono<Map<String, Object>> steamApiFallback(String identifier, Exception exception) {
        log.warn("Steam API fallback triggered for identifier: {}, error: {}", 
                identifier, exception.getMessage());
        
        return Mono.just(Map.of("error", "Service temporarily unavailable", "fallback", true));
    }
}
