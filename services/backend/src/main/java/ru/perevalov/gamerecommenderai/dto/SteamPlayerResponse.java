package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Represents the response from the Steam API for player summaries.
 */
@Data
public class SteamPlayerResponse {

    /**
     * Wrapper object containing the list of players.
     */
    private Response response;

    /**
     * Inner class representing the response wrapper from the Steam API.
     */
    @Data
    public static class Response {
        /**
         * List of Steam players returned by the API.
         */
        private List<Player> players;
    }

    /**
     * Represents a single Steam player.
     */
    @Data
    public static class Player {

        /**
         * Unique Steam ID of the player.
         */
        @JsonProperty("steamid")
        private String steamId;

        /**
         * Display persona name of the player.
         */
        @JsonProperty("personaname")
        private String personaName;

        /**
         * Full URL to the player's avatar image.
         */
        @JsonProperty("avatarfull")
        private String avatarFull;
    }
}
