package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.props.SteamStoreProps;
import ru.perevalov.gamerecommenderai.client.retry.ReactiveRetryStrategy;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

class SteamStoreClientTest {
    private WebClient webClientMock;
    private SteamStoreClient steamStoreClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        webClientMock = Mockito.mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);
        SteamStoreProps steamStoreProps = new SteamStoreProps(
                "https",
                "store.steampowered.com",
                "/api/appdetails/",
                3,
                2L
        );
        //TODO: PCAI-84
        ReactiveRetryStrategy retryStrategy = new ReactiveRetryStrategy();
        steamStoreClient = new SteamStoreClient(webClientMock, steamStoreProps, retryStrategy);
        steamStoreClient.init();
    }

    @Test
    void givenValidJsonResponse_whenFetchGameDetails_thenReturnsCorrectDto() throws Exception {
        /* Given */
        String json = Files.readString(
                Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("steam/fetch_game_details.json")).toURI())
        );

        JsonNode jsonNode = objectMapper.readTree(json);

        Assertions.assertNotNull(jsonNode, "jsonNode should not be null");
        Assertions.assertTrue(jsonNode.has("730"), "JSON must contain '730' key");

        Mono<JsonNode> mockMono = Mono.just(jsonNode);
        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(URI.class))
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .thenReturn(mockMono);

        /* When */
        // TODO: Mono<SteamGameDetailsResponseDto>. Переписать в PCAI-84
        SteamGameDetailsResponseDto actualResponse = steamStoreClient.fetchGameDetails("730").block();

        /* Then */
        Assertions.assertNotNull(actualResponse, "Response should not be null");
        Assertions.assertNotNull(actualResponse.steamGameDataResponseDto(), "Data should not be null");
        Assertions.assertEquals("730", actualResponse.appId(), "App ID should match");
        Assertions.assertTrue(actualResponse.success(), "Success should be true");

        SteamGameDetailsResponseDto.SteamGameDataResponseDto steamGameDataResponseDto = actualResponse.steamGameDataResponseDto();
        Assertions.assertEquals("game", steamGameDataResponseDto.type(), "Type should match");
        Assertions.assertEquals("Counter-Strike 2", steamGameDataResponseDto.name(), "Name should match");
        Assertions.assertEquals(730, steamGameDataResponseDto.steamAppid(), "Steam app ID should match");
        Assertions.assertTrue(steamGameDataResponseDto.isFree(), "Is free should be true");
    }

    @Test
    void givenInvalidJsonResponse_whenFetchGameDetails_thenThrowsException() throws JsonProcessingException {
        /* Given */
        String invalidJsonString = "{\"12345\":{\"success\":true,\"data\":{\"name\":{}}}}";
        JsonNode invalidJsonNode = objectMapper.readTree(invalidJsonString);

        Assertions.assertTrue(invalidJsonNode.has("12345"), "JSON must contain '12345' key");

        Mono<JsonNode> mockMono = Mono.just(invalidJsonNode);

        Mockito.when(webClientMock.get()
                        .uri(ArgumentMatchers.any(URI.class))
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .thenReturn(mockMono);

        Mockito.verify(webClientMock, Mockito.atLeastOnce()).get();
        //TODO: PCAI-84
        /* When */
        GameRecommenderException exception = Assertions.assertThrows(
                GameRecommenderException.class,
                () -> steamStoreClient.fetchGameDetails("12345").block(),
                "Should throw custom exception for mapping error"
        );

        /* Then */
        Assertions.assertEquals(ErrorType.STEAM_JSON_PROCESSING_ERROR, exception.getErrorType());
        Assertions.assertEquals(ErrorType.STEAM_JSON_PROCESSING_ERROR.getDescription(), exception.getErrorType().getDescription());
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getErrorType().getStatus());
    }

}