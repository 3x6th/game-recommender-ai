package ru.perevalov.gamerecommenderai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.time.Duration;
import java.util.List;

@Component
public class SteamClient {

    private static final String GET_PLAYER_SUMMARIES_PATH = "/ISteamUser/GetPlayerSummaries/v0002/";
    private static final String GET_OWNED_GAMES_PATH = "/IPlayerService/GetOwnedGames/v0001/";
    private final WebClient webClient;
    private final String apiKey;

    public SteamClient(WebClient.Builder builder,
                       @Value("${steam.baseUrl}") String baseUrl,
                       @Value("${steam.apiKey}") String apiKey) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public SteamPlayerResponse fetchPlayerSummaries(List<String> steamIds) {
        String idsCsv = String.join(",", steamIds);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GET_PLAYER_SUMMARIES_PATH)
                        .queryParam("key", apiKey)
                        .queryParam("steamids", idsCsv)
                        .build()
                )
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))) // ретраи
                .block();
    }

    public SteamOwnedGamesResponse fetchOwnedGames(String steamId,
                                                   boolean includeAppInfo,
                                                   boolean includePlayedFreeGames) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GET_OWNED_GAMES_PATH)
                        .queryParam("key", apiKey)
                        .queryParam("steamid", steamId)
                        .queryParam("include_appinfo", includeAppInfo)
                        .queryParam("include_played_free_games", includePlayedFreeGames)
                        .build()
                )
                .retrieve()
                .bodyToMono(SteamOwnedGamesResponse.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))) // ретраи
                .block();
    }
}
