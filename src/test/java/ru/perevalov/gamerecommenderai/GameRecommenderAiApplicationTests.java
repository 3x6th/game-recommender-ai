package ru.perevalov.gamerecommenderai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "deepseek.api.key=test-key",
    "deepseek.api.url=https://api.deepseek.com/v1",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameRecommenderAiApplicationTests {

    @Test
    void contextLoads() {
        // Проверяем, что контекст Spring загружается корректно
    }

}
