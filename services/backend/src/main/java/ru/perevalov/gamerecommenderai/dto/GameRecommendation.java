package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecommendation {
    private String title;
    private String genre;
    private String description;
    private String whyRecommended;
    private List<String> platforms;
    private Double rating;
    private String releaseYear;
}
