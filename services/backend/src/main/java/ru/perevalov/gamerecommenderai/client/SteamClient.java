package ru.perevalov.gamerecommenderai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.config.SteamProps;
import ru.perevalov.gamerecommenderai.constant.SteamApiConstant;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.time.Duration;

/**
 * Client for interacting with the Steam Web API.
 * <p>
 * Provides methods to fetch player summaries and owned games using the Steam API.
 * The client handles retries and logging of requests and responses.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SteamClient {

    /**
     * WebClient instance used to make HTTP requests to the Steam API.
     */
    private final WebClient steamWebClient;

    /**
     * Configuration properties for the Steam API, including base URL, API key,
     * endpoint paths, and retry settings.
     */
    private final SteamProps props;

    /**
     * Fetches player summary information for a single Steam ID.
     *
     * @param steamId the Steam ID of the player to fetch
     * @return a {@link SteamPlayerResponse} containing player information
     * @throws RuntimeException if the request to Steam API fails
     */
    public SteamPlayerResponse fetchPlayerSummaries(String steamId) {

        try {
            log.debug("Fetching player summary for steamId={}", steamId);

            SteamPlayerResponse response = steamWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(props.getPlayerSummariesPath())
                            .queryParam(SteamApiConstant.KEY, props.apiKey())
                            .queryParam(SteamApiConstant.STEAMIDS, steamId)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(SteamPlayerResponse.class)
                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
                    .block();

            log.debug("Player summary response: {}", response);

            return response;
        } catch (Exception e) {
            log.error("Error fetching player summaries for steamId={}", steamId, e);
            throw new RuntimeException("Failed to fetch player summaries from Steam API", e);
        }
    }

    /**
     * Fetches the list of games owned by a player.
     *
     * @param steamId                 the Steam ID of the player
     * @param includeAppInfo          whether to include additional app info
     * @param includePlayedFreeGames  whether to include free games played
     * @return a {@link SteamOwnedGamesResponse} containing owned games
     * @throws RuntimeException if the request to Steam API fails
     */
    public SteamOwnedGamesResponse fetchOwnedGames(String steamId,
                                                   boolean includeAppInfo,
                                                   boolean includePlayedFreeGames) {

        try {
            log.debug("Fetching owned games for steamId={}", steamId);

            SteamOwnedGamesResponse response = steamWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(props.getOwnedGamesPath())
                            .queryParam(SteamApiConstant.KEY, props.apiKey())
                            .queryParam(SteamApiConstant.STEAMID, steamId)
                            .queryParam(SteamApiConstant.INCLUDE_APPINFO, includeAppInfo)
                            .queryParam(SteamApiConstant.INCLUDE_PLAYED_FREE_GAMES, includePlayedFreeGames)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(SteamOwnedGamesResponse.class)
                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
                    .block();

            log.debug("Owned games response: {}", response);

            return response;
        } catch (Exception e) {
            log.error("Error fetching owned games for steamId={}", steamId, e);
            throw new RuntimeException("Failed to fetch owned games from Steam API", e);
        }
    }
}
