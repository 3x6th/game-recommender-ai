package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${app.rate-limit.steam-api.max-requests-per-second}")
    private int maxRequestsPerSecond;
    
    @Value("${app.rate-limit.steam-api.max-requests-per-minute}")
    private int maxRequestsPerMinute;
    
    @Value("${app.rate-limit.steam-api.max-requests-per-hour}")
    private int maxRequestsPerHour;
    
    @Value("${app.rate-limit.concurrent-users.max}")
    private int maxConcurrentUsers;

    private final ConcurrentHashMap<String, AtomicInteger> localCounters = new ConcurrentHashMap<>();

    /**
     * Проверяет и инкрементирует лимит запросов в секунду для пользователя.
     * @param userId идентификатор пользователя
     * @return true, если запрос разрешен; иначе false
     */
    public boolean allowSteamApiRequest(String userId) {
        String key = "steam_api:" + userId + ":" + System.currentTimeMillis() / 1000;
        return incrementAndCheckLimit(key, maxRequestsPerSecond, Duration.ofSeconds(1));
    }

    /**
     * Проверяет и инкрементирует лимит запросов в минуту для пользователя.
     * @param userId идентификатор пользователя
     * @return true, если запрос разрешен; иначе false
     */
    public boolean allowSteamApiRequestPerMinute(String userId) {
        String key = "steam_api_minute:" + userId + ":" + System.currentTimeMillis() / 60000;
        return incrementAndCheckLimit(key, maxRequestsPerMinute, Duration.ofMinutes(1));
    }

    /**
     * Проверяет и инкрементирует лимит запросов в час для пользователя.
     * @param userId идентификатор пользователя
     * @return true, если запрос разрешен; иначе false
     */
    public boolean allowSteamApiRequestPerHour(String userId) {
        String key = "steam_api_hour:" + userId + ":" + System.currentTimeMillis() / 3600000;
        return incrementAndCheckLimit(key, maxRequestsPerHour, Duration.ofHours(1));
    }

    /**
     * Регистрирует занятие слота параллельного пользователя.
     * @param userId идентификатор пользователя
     * @return true, если слот выделен; иначе false
     */
    public boolean allowConcurrentUser(String userId) {
        String key = "concurrent_users:" + userId;
        AtomicInteger counter = localCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.get();
        
        if (current < maxConcurrentUsers) {
            return counter.incrementAndGet() <= maxConcurrentUsers;
        }
        return false;
    }

    /**
     * Освобождает ранее занятый слот параллельного пользователя.
     * @param userId идентификатор пользователя
     */
    public void releaseConcurrentUser(String userId) {
        String key = "concurrent_users:" + userId;
        AtomicInteger counter = localCounters.get(key);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    private boolean incrementAndCheckLimit(String key, int limit, Duration ttl) {
        try {
            String currentValue = redisTemplate.opsForValue().get(key);
            int currentCount = currentValue != null ? Integer.parseInt(currentValue) : 0;
            
            if (currentCount >= limit) {
                log.warn("Rate limit exceeded for key: {}, current: {}, limit: {}", key, currentCount, limit);
                return false;
            }
            
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, ttl);
            
            return true;
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // В случае ошибки Redis, разрешаем запрос
            return true;
        }
    }

    /**
     * Проверяет все лимиты (секунда/минута/час) для пользователя.
     * @param userId идентификатор пользователя
     * @return true, если пользователь ограничен по любому из лимитов
     */
    public boolean isRateLimited(String userId) {
        return !allowSteamApiRequest(userId) || 
               !allowSteamApiRequestPerMinute(userId) || 
               !allowSteamApiRequestPerHour(userId);
    }

    /**
     * Сбрасывает счетчики и ключи Redis, связанные с лимитами пользователя.
     * @param userId идентификатор пользователя
     */
    public void resetUserLimits(String userId) {
        String[] patterns = {
            "steam_api:" + userId + ":*",
            "steam_api_minute:" + userId + ":*",
            "steam_api_hour:" + userId + ":*"
        };
        
        for (String pattern : patterns) {
            try {
                redisTemplate.delete(redisTemplate.keys(pattern));
            } catch (Exception e) {
                log.warn("Could not delete keys for pattern: {}", pattern, e);
            }
        }
        
        localCounters.remove("concurrent_users:" + userId);
    }
}
