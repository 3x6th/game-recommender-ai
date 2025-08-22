package ru.perevalov.gamerecommenderai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAspectJAutoProxy
@ConfigurationPropertiesScan
public class GameRecommenderAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameRecommenderAiApplication.class, args);
    }

}
