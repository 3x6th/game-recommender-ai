package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecommendationRequest {
    private String preferences;
    private Integer maxRecommendations;
    private String context;
} 