package ru.perevalov.gamerecommenderai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiContextBuilderFactory {

    @Value("${app.recommender.defaults.language}")
    private String defaultLanguage;

    @Value("${app.recommender.defaults.max-results}")
    private int defaultMaxResults;

    public  AiContextBuilder create() {
        return new AiContextBuilder(defaultLanguage,defaultMaxResults);
    }

}