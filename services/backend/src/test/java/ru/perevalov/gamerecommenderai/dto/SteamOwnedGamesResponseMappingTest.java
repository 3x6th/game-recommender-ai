package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SteamOwnedGamesResponseMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testOwnedGamesMapping() throws Exception {

        String json = """
                {
                  "response": {
                    "game_count": 2,
                    "games": [
                      {
                        "appid": 570,
                        "name": "Dota 2",
                        "playtime_forever": 1234
                      },
                      {
                        "appid": 730,
                        "name": "CS:GO",
                        "playtime_forever": 567
                      }
                    ]
                  }
                }
                """;

        SteamOwnedGamesResponse response = objectMapper.readValue(json, SteamOwnedGamesResponse.class);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertEquals(2, response.getResponse().getGameCount());
        assertEquals(2, response.getResponse().getGames().size());

        var game1 = response.getResponse().getGames().getFirst();
        assertEquals(570, game1.getAppId());
        assertEquals("Dota 2", game1.getName());
        assertEquals(1234, game1.getPlaytimeForever());

        var game2 = response.getResponse().getGames().get(1);
        assertEquals(730, game2.getAppId());
        assertEquals("CS:GO", game2.getName());
        assertEquals(567, game2.getPlaytimeForever());
    }
}
