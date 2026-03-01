package ru.perevalov.gamerecommenderai.entity.embedded;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnedGamesSnapshot {

    private Response response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private Integer gameCount;

        private List<Game> games;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Game {

        private Long appId;

        private String name;

        private Integer playtime2weeks;

        private Integer playtimeForever;

        private Integer rtimeLastPlayed;
    }
}
