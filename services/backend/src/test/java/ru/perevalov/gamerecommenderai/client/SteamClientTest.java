package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.config.SteamProps;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Function;

class SteamClientTest {
    private WebClient webClientMock;
    private SteamClient steamClient;

    @BeforeEach
    void setUp() {
        webClientMock = Mockito.mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);

        SteamProps steamProps = new SteamProps(
                "https://dummy.url",
                "dummyKey",
                "/ISteamUser/GetPlayerSummaries/v0002/",
                "/IPlayerService/GetOwnedGames/v0001/",
                3,
                5L
        );

        steamClient = new SteamClient(webClientMock, steamProps);
    }

    @Test
    void testFetchPlayerSummaries_mapping() throws Exception {

        String json = Files.readString(
                Paths.get(getClass().getClassLoader().getResource("steam/player_summaries.json").toURI())
        );

        SteamPlayerResponse mappedResponse = new ObjectMapper()
                .readValue(json, SteamPlayerResponse.class);

        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(Function.class))
                        .retrieve()
                        .bodyToMono(SteamPlayerResponse.class))
                .thenReturn(Mono.just(mappedResponse));

        SteamPlayerResponse response = steamClient.fetchPlayerSummaries("76561198000000000");

        Assertions.assertNotNull(response.getResponse(), "Response should not be null");
        Assertions.assertEquals(1, response.getResponse().getPlayers().size(), "Response should have one player");
        var player = response.getResponse().getPlayers().getFirst();
        Assertions.assertEquals("76561198000000000", player.getSteamId(), "Steam id should match");
        Assertions.assertEquals("TestUser", player.getPersonaName(), "Persona name should match");
        Assertions.assertEquals("http://example.com/avatar.jpg", player.getAvatarFull(), "Avatar full should match");
    }

    @Test
    void testFetchOwnedGames_mapping() throws Exception {

        String json = Files.readString(
                Paths.get(getClass().getClassLoader().getResource("steam/fetch_owned_games.json").toURI())
        );

        SteamOwnedGamesResponse mappedResponse = new ObjectMapper()
                .readValue(json, SteamOwnedGamesResponse.class);

        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(Function.class))
                        .retrieve()
                        .bodyToMono(SteamOwnedGamesResponse.class))
                .thenReturn(Mono.just(mappedResponse));

        SteamOwnedGamesResponse response = steamClient.fetchOwnedGames("76561198000000000", true, true);

        Assertions.assertNotNull(response.getResponse(), "Response should not be null");
        Assertions.assertEquals(2, response.getResponse().getGameCount(), "Response should have two games");
        Assertions.assertEquals(2, response.getResponse().getGames().size(), "Response should have two games");

        var game1 = response.getResponse().getGames().get(0);
        Assertions.assertEquals(440, game1.getAppId(), "App id should match");
        Assertions.assertEquals("Team Fortress 2", game1.getName(), "Name should match");
        Assertions.assertEquals(1234, game1.getPlaytimeForever(), "Playtime forever should match");
        Assertions.assertEquals(50, game1.getPlaytime2weeks(), "Playtime 2weeks should match");

        var game2 = response.getResponse().getGames().get(1);
        Assertions.assertEquals(570, game2.getAppId(), "App id should match");
        Assertions.assertEquals("Dota 2", game2.getName(), "Name should match");
        Assertions.assertEquals(5678, game2.getPlaytimeForever(), "Playtime forever should match");
        Assertions.assertEquals(100, game2.getPlaytime2weeks(), "Playtime 2weeks should match");
    }

    @Test
    void testFetchPlayerSummaries_privateProfile_returnsEmptyPlayers() {

        SteamPlayerResponse emptyProfile = new SteamPlayerResponse();

        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(Function.class))
                        .retrieve()
                        .bodyToMono(SteamPlayerResponse.class))
                .thenReturn(Mono.just(emptyProfile));

        SteamPlayerResponse response = steamClient.fetchPlayerSummaries("123456");

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertTrue(response.getResponse() == null
                || response.getResponse().getPlayers() == null
                || response.getResponse().getPlayers().isEmpty(), "Response should have empty players for private profile");
    }

    @Test
    void testFetchOwnedGames_emptyLibrary_returnsZeroGames() {

        SteamOwnedGamesResponse emptyGames = new SteamOwnedGamesResponse();
        SteamOwnedGamesResponse.Response inner = new SteamOwnedGamesResponse.Response();
        inner.setGameCount(0);
        inner.setGames(Collections.emptyList());
        emptyGames.setResponse(inner);

        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(Function.class))
                        .retrieve()
                        .bodyToMono(SteamOwnedGamesResponse.class))
                .thenReturn(Mono.just(emptyGames));

        SteamOwnedGamesResponse response = steamClient.fetchOwnedGames("123456", true, true);

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertNotNull(response.getResponse(), "Response should not be null");
        Assertions.assertEquals(0, response.getResponse().getGameCount(), "Response should have zero games");
        Assertions.assertTrue(response.getResponse().getGames().isEmpty(), "Response should have empty games");
    }
}
