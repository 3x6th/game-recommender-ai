package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SteamOwnedGamesResponse {
    private Response response;

    @Data
    public static class Response {
        @JsonProperty("game_count")
        private int gameCount;
        private List<Game> games;
    }

    @Data
    public static class Game {
        @JsonProperty("appid")
        private long appId;
        private String name;
        @JsonProperty("playtime_2weeks")
        private int playtime2weeks;
        @JsonProperty("playtime_forever")
        private int playtimeForever;
    }
}
