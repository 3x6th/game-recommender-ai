package ru.perevalov.gamerecommenderai.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Базовый класс для интеграционных тестов на Testcontainers.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:17.4")
                    .withDatabaseName("test-db")
                    .withUsername("postgres")
                    .withPassword("postgres");
    @Container
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    public static void dynamicPropertySource(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRE_SQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRE_SQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRE_SQL_CONTAINER::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.url", POSTGRE_SQL_CONTAINER::getJdbcUrl);
        registry.add("spring.liquibase.user", POSTGRE_SQL_CONTAINER::getUsername);
        registry.add("spring.liquibase.password", POSTGRE_SQL_CONTAINER::getPassword);

        // R2DBC (reactive repositories)
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s?currentSchema=game_recommender",
                POSTGRE_SQL_CONTAINER.getHost(),
                POSTGRE_SQL_CONTAINER.getMappedPort(5432),
                POSTGRE_SQL_CONTAINER.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", POSTGRE_SQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRE_SQL_CONTAINER::getPassword);

        // Отключаем scheduled job'ы в интеграционных тестах, чтобы не ловить фоновые побочные эффекты.
        registry.add("spring.task.scheduling.enabled", () -> "false");

        // Ослабляем rate limit для интеграционных тестов, чтобы повторные запросы от одного тестового клиента
        // не падали с 429 раньше, чем будет проверена бизнес-логика.
        registry.add("performance.rate-limiter.role.limit.of-hour.GUEST_USER", () -> "1000");
        registry.add("performance.rate-limiter.role.limit.of-hour.USER", () -> "1000");

        // Redis
        registry.add("redis.redis-uri", () -> String.format("redis://%s:%d",
                REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
    }
}
