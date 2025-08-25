package ru.perevalov.gamerecommenderai.client;

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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteamServiceEdgeCasesTest {

    private WebClient webClientMock;
    private SteamService steamService;

    @BeforeEach
    void setUp() {
        webClientMock = mock(WebClient.class, RETURNS_DEEP_STUBS);
        steamService = new SteamService(webClientMock);
        steamService.setApiKey("dummyKey");
    }

    @Test
    void testPrivateProfile_returnsEmptyPlayers() {
        when(webClientMock.get()
                .uri(any(Function.class))
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class))
                .thenReturn(Mono.just(new SteamPlayerResponse()));

        SteamPlayerResponse response = steamService.getPlayerSummaries(List.of("123456"));

        assertNotNull(response);

        assertTrue(response.getResponse() == null
                || response.getResponse().getPlayers() == null
                || response.getResponse().getPlayers().isEmpty());
    }

    @Test
    void testEmptyLibrary_returnsZeroGames() {
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

        SteamOwnedGamesResponse response = steamService.getOwnedGames("123456", true, true);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertEquals(0, response.getResponse().getGameCount());
        assertTrue(response.getResponse().getGames().isEmpty());
    }
}
