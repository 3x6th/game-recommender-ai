package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Model for the Steam API response when fetching a user's owned games.
 */
@Data
public class SteamOwnedGamesResponse {

    /**
     * Contains the games data and their count.
     */
    private Response response;

    /**
     * Inner class representing the response wrapper from the Steam API.
     */
    @Data
    public static class Response {

        /**
         * Total number of games owned by the user.
         */
        @JsonProperty("game_count")
        private int gameCount;

        /**
         * List of the user's games.
         */
        private List<Game> games;
    }

    /**
     * Model for a single game owned by the user.
     */
    @Data
    public static class Game {

        /**
         * Steam App ID of the game.
         */
        @JsonProperty("appid")
        private long appId;

        /**
         * Name of the game.
         */
        private String name;

        /**
         * Playtime in the last 2 weeks, in minutes.
         */
        @JsonProperty("playtime_2weeks")
        private int playtime2weeks;

        /**
         * Total playtime, in minutes.
         */
        @JsonProperty("playtime_forever")
        private int playtimeForever;
    }
}
