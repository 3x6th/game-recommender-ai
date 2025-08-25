package ru.perevalov.gamerecommenderai.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SteamPlayerResponseMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testPlayerResponseMapping() throws JsonProcessingException {

        String json = """
                {
                  "response": {
                    "players": [
                      {
                        "steamid": "76561198000000000",
                        "personaname": "TestUser",
                        "avatarfull": "http://example.com/avatar.jpg"
                      }
                    ]
                  }
                }
                """;

        SteamPlayerResponse response = mapper.readValue(json, SteamPlayerResponse.class);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertEquals(1, response.getResponse().getPlayers().size());

        var player = response.getResponse().getPlayers().getFirst();

        assertEquals("76561198000000000", player.getSteamId());
        assertEquals("TestUser", player.getPersonaName());
        assertEquals("http://example.com/avatar.jpg", player.getAvatarFull());
    }
}
