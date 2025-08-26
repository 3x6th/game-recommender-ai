package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class Resilience4jTest {

    @Autowired
    private GameRecommenderService gameRecommenderService;

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void testCircuitBreakerFallback() {
        // Тест должен вернуть fallback ответ при недоступности gRPC сервиса
        GameRecommendationResponse response = gameRecommenderService.getGameRecommendation("test preferences");
        
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getRecommendation().contains("Сервис временно недоступен"));
    }

    @Test
    void testRateLimiting() {
        String userId = "test-user-123";
        
        // Сброс лимитов для тестового пользователя
        rateLimitService.resetUserLimits(userId);
        
        // Проверяем, что первые запросы разрешены
        assertTrue(rateLimitService.allowSteamApiRequest(userId));
        assertTrue(rateLimitService.allowSteamApiRequestPerMinute(userId));
        assertTrue(rateLimitService.allowSteamApiRequestPerHour(userId));
        
        // Проверяем лимит параллельных пользователей
        assertTrue(rateLimitService.allowConcurrentUser(userId));
        
        // Освобождаем пользователя
        rateLimitService.releaseConcurrentUser(userId);
    }

    @Test
    void testConcurrentUsersLimit() {
        String userId = "concurrent-test-user";
        rateLimitService.resetUserLimits(userId);
        
        // Симулируем 100 параллельных пользователей
        for (int i = 0; i < 100; i++) {
            assertTrue(rateLimitService.allowConcurrentUser(userId + i));
        }
        
        // 101-й пользователь должен быть заблокирован
        assertFalse(rateLimitService.allowConcurrentUser(userId + "101"));
        
        // Освобождаем всех пользователей
        for (int i = 0; i < 100; i++) {
            rateLimitService.releaseConcurrentUser(userId + i);
        }
    }
}
