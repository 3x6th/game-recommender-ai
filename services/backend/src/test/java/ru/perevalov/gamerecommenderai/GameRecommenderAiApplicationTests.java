package ru.perevalov.gamerecommenderai;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "deepseek.api.key=test-key",
    "deepseek.api.url=https://api.deepseek.com/v1",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameRecommenderAiApplicationTests {

    @MockitoBean
    private RedisClient redisClient;

    @MockitoBean
    private StatefulRedisConnection<byte[], byte[]> redisConnection;

    @MockitoBean
    private LettuceBasedProxyManager<byte[]> proxyManager;

    @MockitoBean
    private Bucket apiBucket;

    @Test
    void contextLoads() {
        // Проверяем, что контекст Spring загружается корректно
    }

}
