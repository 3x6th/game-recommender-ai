package ru.perevalov.gamerecommenderai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "deepseek")
@Getter
@Setter
public class DeepSeekConfig {
    private Api api = new Api();
    private String model = "deepseek-chat";
    private Integer maxTokens = 1000;
    private Double temperature = 0.7;

    @Getter
    @Setter
    public static class Api {
        private String url = "https://api.deepseek.com/v1";
        private String key;
    }
} 