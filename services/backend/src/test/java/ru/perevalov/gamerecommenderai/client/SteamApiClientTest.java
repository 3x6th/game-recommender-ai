package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.props.SteamApiProps;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;


class SteamApiClientTest {
    private SteamApiClient steamApiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SneakyThrows
    void setUp() {
        WebClient mockBaseClient = Mockito.mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);
        WebClient.Builder mockBuilder = Mockito.mock(WebClient.Builder.class, Mockito.RETURNS_SELF);

        Mockito.when(mockBaseClient.mutate()).thenReturn(mockBuilder);
        Mockito.when(mockBuilder.exchangeStrategies(Mockito.<ExchangeStrategies>any())).thenReturn(mockBuilder);
        Mockito.when(mockBuilder.clientConnector(Mockito.any())).thenReturn(mockBuilder);
        Mockito.when(mockBuilder.defaultHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(mockBuilder);

        WebClient mockCustomClient = Mockito.mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mockBuilder.build()).thenReturn(mockCustomClient);

        String json = Files.readString(
                Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("steam/fetch_steam_apps.json")).toURI())
        );
        SteamAppResponseDto expectedResponse = objectMapper.readValue(json, SteamAppResponseDto.class);

        Mockito.when(mockCustomClient.get()
                        .uri(Mockito.any(URI.class))
                        .retrieve()
                        .bodyToMono(SteamAppResponseDto.class))
                .thenReturn(Mono.just(expectedResponse));

        SteamApiProps steamApiProps = new SteamApiProps(
                "https", "store.steampowered.com", "/ISteamApps/GetAppList/v2", 3, 2, 3, 0.5, 5, 20971520
        );
        steamApiClient = new SteamApiClient(steamApiProps, mockBaseClient);
        steamApiClient.init();
    }

    @Test
    void fetchSteamApps_returnsExpectedResponse() {
        // TODO: Переделать в PCAI-84
        SteamAppResponseDto response = steamApiClient.fetchSteamApps().block();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.appList(), "applist should not be null");
        Assertions.assertNotNull(response.appList().apps(), "apps list should not be null");
        Assertions.assertFalse(response.appList().apps().isEmpty(), "apps list should not be empty");

        var apps = response.appList().apps();

        Assertions.assertEquals(5, apps.getFirst().appid(), "First appId should be 5");
        Assertions.assertEquals("Dedicated Server", apps.getFirst().name(), "First app name should be 'Dedicated Server'");

    }
}
