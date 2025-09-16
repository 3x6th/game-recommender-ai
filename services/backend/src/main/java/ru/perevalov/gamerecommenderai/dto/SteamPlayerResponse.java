package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Представляет ответ от Steam API для получения сводной информации об игроках.
 */
@Data
public class SteamPlayerResponse {

    /**
     * Объект-обёртка, содержащий список игроков.
     */
    private Response response;

    /**
     * Внутренний класс, представляющий обёртку ответа Steam API.
     */
    @Data
    public static class Response {

        /**
         * Список игроков, возвращаемых API.
         */
        private List<Player> players;
    }

    /**
     * Модель одного игрока Steam.
     */
    @Data
    public static class Player {

        /**
         * Уникальный Steam ID игрока.
         */
        @JsonProperty("steamid")
        private String steamId;

        /**
         * Отображаемое имя (persona name) игрока.
         */
        @JsonProperty("personaname")
        private String personaName;

        /**
         * Полный URL аватарки игрока.
         */
        @JsonProperty("avatarfull")
        private String avatarFull;
    }
}
