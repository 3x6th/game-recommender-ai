package ru.perevalov.gamerecommenderai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationResponse {
    private boolean success;
    private String message;
    private String provider;
    private List<GameRecommendation> recommendations;
}
