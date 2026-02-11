package ru.perevalov.gamerecommenderai.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIT {

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

        // R2DBC (reactive repositories)
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s?schema=game_recommender",
                POSTGRE_SQL_CONTAINER.getHost(),
                POSTGRE_SQL_CONTAINER.getMappedPort(5432),
                POSTGRE_SQL_CONTAINER.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", POSTGRE_SQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRE_SQL_CONTAINER::getPassword);

        // Disable scheduled jobs for integration tests (avoid flaky background calls)
        registry.add("spring.task.scheduling.enabled", () -> "false");

        // Redis
        registry.add("redis.redis-uri", () -> String.format("redis://%s:%d",
                REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
    }
}
