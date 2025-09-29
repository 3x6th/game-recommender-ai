package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Модель ответа Steam API при получении списка игр пользователя.
 */
@Data
public class SteamOwnedGamesResponse {

    /**
     * Содержит данные об играх и их количестве.
     */
    private Response response;

    /**
     * Внутренний класс, представляющий обёртку ответа Steam API.
     */
    @Data
    public static class Response {

        /**
         * Общее количество игр у пользователя.
         */
        @JsonProperty("game_count")
        private int gameCount;

        /**
         * Список игр пользователя.
         */
        private List<Game> games;
    }

    /**
     * Модель отдельной игры пользователя.
     */
    @Data
    public static class Game {

        /**
         * Steam App ID игры.
         */
        @JsonProperty("appid")
        private long appId;

        /**
         * Название игры.
         */
        private String name;

        /**
         * Время игры за последние 2 недели в минутах.
         */
        @JsonProperty("playtime_2weeks")
        private int playtime2weeks;

        /**
         * Общее время игры в минутах.
         */
        @JsonProperty("playtime_forever")
        private int playtimeForever;
    }
}
