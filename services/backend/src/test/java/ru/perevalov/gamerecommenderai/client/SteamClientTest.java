package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteamClientTest {
    private WebClient webClientMock;
    private WebClient.Builder webClientBuilderMock;
    private SteamClient steamClient;

    @BeforeEach
    void setUp() {
        webClientMock = mock(WebClient.class, RETURNS_DEEP_STUBS);

        webClientBuilderMock = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);
        when(webClientBuilderMock.baseUrl(anyString())).thenReturn(webClientBuilderMock);
        when(webClientBuilderMock.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilderMock);
        when(webClientBuilderMock.build()).thenReturn(webClientMock);

        steamClient = new SteamClient(webClientBuilderMock, "https://dummy.url", "dummyKey");
    }

    @Test
    void testFetchPlayerSummaries_mapping() throws Exception {

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

        SteamPlayerResponse mappedResponse = new ObjectMapper()
                .readValue(json, SteamPlayerResponse.class);

        when(webClientMock.get()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class))
                .thenReturn(Mono.just(mappedResponse));

        SteamPlayerResponse response = steamClient.fetchPlayerSummaries(List.of("76561198000000000"));

        assertNotNull(response.getResponse());
        assertEquals(1, response.getResponse().getPlayers().size());
        var player = response.getResponse().getPlayers().getFirst();
        assertEquals("76561198000000000", player.getSteamId());
        assertEquals("TestUser", player.getPersonaName());
        assertEquals("http://example.com/avatar.jpg", player.getAvatarFull());
    }

    @Test
    void testFetchOwnedGames_mapping() throws Exception {

        String json = """
                {
                  "response": {
                    "game_count": 2,
                    "games": [
                      {"appid": 440, "name": "Team Fortress 2", "playtime_forever": 1234},
                      {"appid": 570, "name": "Dota 2", "playtime_forever": 5678}
                    ]
                  }
                }
                """;

        SteamOwnedGamesResponse mappedResponse = new ObjectMapper()
                .readValue(json, SteamOwnedGamesResponse.class);

        when(webClientMock.get()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(SteamOwnedGamesResponse.class))
                .thenReturn(Mono.just(mappedResponse));

        SteamOwnedGamesResponse response = steamClient.fetchOwnedGames("76561198000000000", true, true);

        assertNotNull(response.getResponse());
        assertEquals(2, response.getResponse().getGameCount());
        assertEquals(2, response.getResponse().getGames().size());

        var game1 = response.getResponse().getGames().getFirst();
        assertEquals(440, game1.getAppId());
        assertEquals("Team Fortress 2", game1.getName());
        assertEquals(1234, game1.getPlaytimeForever());

        var game2 = response.getResponse().getGames().get(1);
        assertEquals(570, game2.getAppId());
        assertEquals("Dota 2", game2.getName());
        assertEquals(5678, game2.getPlaytimeForever());
    }

    @Test
    void testFetchPlayerSummaries_PrivateProfile_returnsEmptyPlayers() {

        SteamPlayerResponse emptyProfile = new SteamPlayerResponse();
        when(webClientMock.get()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class))
                .thenReturn(Mono.just(emptyProfile));

        SteamPlayerResponse response = steamClient.fetchPlayerSummaries(List.of("123456"));

        assertNotNull(response);
        assertTrue(response.getResponse() == null
                || response.getResponse().getPlayers() == null
                || response.getResponse().getPlayers().isEmpty());
    }

    @Test
    void testFetchOwnedGames_EmptyLibrary_returnsZeroGames() {

        SteamOwnedGamesResponse emptyGames = new SteamOwnedGamesResponse();
        SteamOwnedGamesResponse.Response inner = new SteamOwnedGamesResponse.Response();
        inner.setGameCount(0);
        inner.setGames(Collections.emptyList());
        emptyGames.setResponse(inner);

        when(webClientMock.get()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(SteamOwnedGamesResponse.class))
                .thenReturn(Mono.just(emptyGames));

        SteamOwnedGamesResponse response = steamClient.fetchOwnedGames("123456", true, true);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertEquals(0, response.getResponse().getGameCount());
        assertTrue(response.getResponse().getGames().isEmpty());
    }
}
