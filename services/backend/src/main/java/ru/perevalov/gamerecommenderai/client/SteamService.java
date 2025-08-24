package ru.perevalov.gamerecommenderai.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SteamService {

    private final WebClient steamWebClient;

    @Value("${steam.apiKey}")
    private String apiKey;
    private static final String GET_PLAYER_SUMMARIES_PATH = "/ISteamUser/GetPlayerSummaries/v0002/";
    private static final String GET_OWNED_GAMES_PATH = "/IPlayerService/GetOwnedGames/v0001/";

    public SteamPlayerResponse getPlayerSummaries(List<String> steamIds) {
        String idsCsv = String.join(",", steamIds);

        return steamWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GET_PLAYER_SUMMARIES_PATH)
                        .queryParam("key", apiKey)
                        .queryParam("steamids", idsCsv)
                        .build()
                )
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class)
                .block(); // синхронный вызов (как в Feign)
    }

    public SteamOwnedGamesResponse getOwnedGames(String steamId,
                                                 boolean includeAppInfo,
                                                 boolean includePlayedFreeGames) {
        return steamWebClient.get()
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
                .block(); // тоже синхронно
    }
}
