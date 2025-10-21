package ru.perevalov.gamerecommenderai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// TODO: Удалить exclude после завершения задачи PCAI-78
@SpringBootApplication(exclude = {
        R2dbcAutoConfiguration.class,
        R2dbcDataAutoConfiguration.class,
        R2dbcRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties
@EnableCaching
@EnableAspectJAutoProxy
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
@EnableR2dbcAuditing
public class GameRecommenderAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameRecommenderAiApplication.class, args);
    }

}
