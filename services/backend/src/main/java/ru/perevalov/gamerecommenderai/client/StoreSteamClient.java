package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.config.StoreSteamProps;
import ru.perevalov.gamerecommenderai.constant.SteamApiConstant;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDataResponseDto;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.time.Duration;

/**
 * Client for interacting with the Steam Store Web API.
 * <p>
 * This client provides methods to retrieve detailed information about Steam games from the Store API.
 * It handles retry logic for failed requests and includes logging for request tracing.
 * </p>
 * <p>
 * This client depends on {@link StoreSteamProps} for configuration (e.g., base URL components, retry settings)
 * and uses constants from {@link SteamApiConstant} for query parameters and JSON nodes.
 * </p>
 *
 * @author Alexandr Petrovich
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StoreSteamClient {
    private final WebClient steamWebClient;

    private final StoreSteamProps props;

    /**
     * Fetches detailed information for one or more Steam apps.
     * <p>
     * This method queries the Steam Store API for app details using the provided app IDs.
     * For single appId, returns structured DTO with appId, success status, and game data.
     * </p>
     * <p>
     * Example appId: "730" for Counter-Strike 2.
     * </p>
     *
     * @param appId Steam app IDs (e.g., "730")
     * @return {@link SteamGameDetailsResponseDto} containing appId, success, and game data for the app
     * @throws GameRecommenderException if the request to Steam Store API fails after retries
     */
    public SteamGameDetailsResponseDto fetchGameDetails(String appId) {
        try {
            log.debug("Fetching app details for appId={}", appId);
            String uri = buildUri(appId);

            JsonNode rawResponse = steamWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
                    .block();

            return parseJson(appId, rawResponse);
        } catch (JsonProcessingException e) {
            log.error("JSON parsing failed for appId={}", appId, e);
            throw new GameRecommenderException(ErrorType.STEAM_APP_DETAILS_NOT_FOUND, appId);
        } catch (Exception e) {
            log.error("Error fetching app details for appId={}", appId, e);
            throw new GameRecommenderException(ErrorType.STEAM_STORE_API_FETCH_APP_DETAILS_ERROR, appId);
        }
    }

    /**
     * Builds the full URI for the Steam Store API request.
     *
     * @param appIds comma-separated app IDs
     * @return full URI string (e.g., "<a href="https://store.steampowered.com/api/appdetails?appids=730">...</a>")
     */
    private @NotNull String buildUri(String appIds) {
        String uri = UriComponentsBuilder.newInstance()
                .scheme(props.scheme())
                .host(props.host())
                .path(props.getAppDetailsPath())
                .queryParam(SteamApiConstant.APP_IDS, appIds)
                .build()
                .toUriString();
        log.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Parses the raw JSON response into {@link SteamGameDetailsResponseDto}.
     * <p>
     * Handles the API structure: {"appIds": {"success": boolean, "data": {...}}}.
     * Extracts success and data for the first appId (extend for multiple if needed).
     * </p>
     *
     * @param appIds      app IDs from request
     * @param rawResponse raw JsonNode from API
     * @return parsed DTO with appId, success, and game data
     * @throws GameRecommenderException if data node missing or invalid
     * @throws JsonProcessingException  if mapping to DTO fails
     */
    private SteamGameDetailsResponseDto parseJson(String appIds, JsonNode rawResponse) throws JsonProcessingException {
        if (rawResponse != null) {
            log.debug("Raw response: {}", rawResponse.asText());
            JsonNode appNode = rawResponse.get(appIds);

            if (appNode.has(SteamApiConstant.DATA)) {
                ObjectMapper mapper = new ObjectMapper();
                SteamGameDataResponseDto data = mapper.treeToValue(appNode.get("data"), SteamGameDataResponseDto.class);

                SteamGameDetailsResponseDto response = SteamGameDetailsResponseDto.builder()
                        .appId(appIds)
                        .success(appNode.at(SteamApiConstant.SUCCESS).asBoolean())
                        .steamGameDataResponseDto(data)
                        .build();

                log.debug("Decoded full steamGameDetailsResponseDto: {}", response);
                return response;
            } else {
                throw new GameRecommenderException(ErrorType.STEAM_APP_DETAILS_NOT_FOUND, appIds);
            }
        } else {
            throw new GameRecommenderException(ErrorType.STEAM_APP_DETAILS_NOT_FOUND, appIds);
        }
    }
}
