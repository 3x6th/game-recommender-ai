package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SteamPlayerResponse {
    private Response response;

    @Data
    public static class Response {
        private List<Player> players;
    }

    @Data
    public static class Player {
        @JsonProperty("steamid")
        private String steamId;

        @JsonProperty("personaname")
        private String personaName;

        @JsonProperty("avatarfull")
        private String avatarFull;
    }
}
