package ru.perevalov.gamerecommenderai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.constant.SteamApiConstant;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.time.Duration;

@Component
@Slf4j
public class SteamClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String getPlayerSummariesPath;
    private final String getOwnedGamesPath;
    @Value("${steam.retry.attempts}")
    private int retryAttempts;
    @Value("${steam.retry.delaySeconds}")
    private long retryDelaySeconds;

    public SteamClient(WebClient.Builder builder,
                       @Value("${steam.baseUrl}") String baseUrl,
                       @Value("${steam.apiKey}") String apiKey,
                       @Value("${steam.getPlayerSummariesPath}") String getPlayerSummariesPath,
                       @Value("${steam.getOwnedGamesPath}") String getOwnedGamesPath) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.getPlayerSummariesPath = getPlayerSummariesPath;
        this.getOwnedGamesPath = getOwnedGamesPath;
    }

    public SteamPlayerResponse fetchPlayerSummaries(String steamId) {

        try {
            log.debug("Fetching player summary for steamId={}", steamId);

            SteamPlayerResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(getPlayerSummariesPath)
                            .queryParam(SteamApiConstant.KEY, apiKey)
                            .queryParam(SteamApiConstant.STEAMIDS, steamId)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(SteamPlayerResponse.class)
                    .retryWhen(Retry.fixedDelay(retryAttempts, Duration.ofSeconds(retryDelaySeconds)))
                    .block();

            log.debug("Player summary response: {}", response);

            return response;
        } catch (Exception e) {
            log.error("Error fetching player summaries for steamId={}", steamId, e);
            throw new RuntimeException("Failed to fetch player summaries from Steam API", e);
        }
    }

    public SteamOwnedGamesResponse fetchOwnedGames(String steamId,
                                                   boolean includeAppInfo,
                                                   boolean includePlayedFreeGames) {

        try {
            log.debug("Fetching owned games for steamId={}", steamId);

            SteamOwnedGamesResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(getOwnedGamesPath)
                            .queryParam(SteamApiConstant.KEY, apiKey)
                            .queryParam(SteamApiConstant.STEAMID, steamId)
                            .queryParam(SteamApiConstant.INCLUDE_APPINFO, includeAppInfo)
                            .queryParam(SteamApiConstant.INCLUDE_PLAYED_FREE_GAMES, includePlayedFreeGames)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(SteamOwnedGamesResponse.class)
                    .retryWhen(Retry.fixedDelay(retryAttempts, Duration.ofSeconds(retryDelaySeconds)))
                    .block();

            log.debug("Owned games response: {}", response);

            return response;
        } catch (Exception e) {
            log.error("Error fetching owned games for steamId={}", steamId, e);
            throw new RuntimeException("Failed to fetch owned games from Steam API", e);
        }
    }
}
