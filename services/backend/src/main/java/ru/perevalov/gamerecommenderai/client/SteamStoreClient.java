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
import ru.perevalov.gamerecommenderai.config.SteamStoreProps;
import ru.perevalov.gamerecommenderai.constant.SteamApiConstant;
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
 * This client depends on {@link SteamStoreProps} for configuration (e.g., base URL components, retry settings)
 * and uses constants from {@link SteamApiConstant} for query parameters and JSON nodes.
 * </p>
 *
 * @author Alexandr Petrovich
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SteamStoreClient {
    private final WebClient steamWebClient;

    private final SteamStoreProps props;

    /**
     * Fetches detailed information for a single Steam app from the Steam Store API.
     * <p>
     * This method queries the Steam Store API for app details using the provided app ID.
     * It performs HTTP GET with retries (fixed delay) on failure. If successful, parses the JSON response
     * into a structured DTO containing appId, success status from the API, and game data (e.g., name, type, description).
     * </p>
     * <p>
     * Example: For appId "730" (Counter-Strike 2), returns DTO with success=true and game data like name="Counter-Strike 2", type="game".
     * Handles single app ID only.
     * </p>
     *
     * @param appId single Steam app ID (e.g., "730")
     * @return {@link SteamGameDetailsResponseDto} with appId, success flag, and parsed game data if API call succeeds
     * @throws GameRecommenderException with specific ErrorType:
     *  <ul>
     *    <li>{@link ErrorType#STEAM_APP_DETAILS_NOT_FOUND}: App ID not found in API response or response is null</li>
     *    <li>{@link ErrorType#STEAM_DATA_IN_APP_DETAILS_NOT_FOUND}: "data" node missing in API response structure</li>
     *    <li>{@link ErrorType#STEAM_JSON_PROCESSING_ERROR}: Invalid JSON structure during deserialization (e.g., malformed data node)</li>
     *    <li>{@link ErrorType#STEAM_STORE_API_FETCH_APP_DETAILS_ERROR}: Network/HTTP failure, timeout, or other runtime
     *    errors (after retries)</li>
     *  </ul>
     */
    public SteamGameDetailsResponseDto fetchGameDetails(String appId) {
        try {
            log.debug("Fetching app details for appId={}", appId);
            String uri = buildUri(appId);

            JsonNode steamApiFullResponse = steamWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
                    .block();

            if (steamApiFullResponse == null) {
                throw new GameRecommenderException(ErrorType.STEAM_APP_DETAILS_NOT_FOUND, appId);
            }

            return parseJson(appId, steamApiFullResponse);
        } catch (GameRecommenderException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.error("JSON processing error for appId={}, invalid structure in Steam API response", appId, e);
            throw new GameRecommenderException(ErrorType.STEAM_JSON_PROCESSING_ERROR);
        } catch (Exception e) {
            log.error("Error fetching app details for appId={}", appId, e);
            throw new GameRecommenderException(ErrorType.STEAM_STORE_API_FETCH_APP_DETAILS_ERROR, appId);
        }
    }

    /**
     * Builds the full URI for the Steam Store API request.
     *
     * @param appId Steam app ID
     * @return full URI string (e.g., "<a href="https://store.steampowered.com/api/appdetails?appids=730">...</a>")
     */
    private @NotNull String buildUri(String appId) {
        String uri = UriComponentsBuilder.newInstance()
                .scheme(props.scheme())
                .host(props.host())
                .path(props.getAppDetailsPath())
                .queryParam(SteamApiConstant.APP_IDS, appId)
                .build()
                .toUriString();
        log.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Parses the Steam API JSON response into {@link SteamGameDetailsResponseDto}.
     * <p>
     * API structure: [{"appId": {"success": boolean, "data": {game details}}}].
     * First, extracts the upper app wrapper node for the given appId (e.g., "730": {} containing success and data pointer).
     * From the upper wrapper, extracts the success flag (boolean from {"success": true}).
     * Then, extracts the lower "data" node (game details object, e.g., {"type": "game", "name": "Counter-Strike 2", hero...})
     * and maps it to SteamGameDataResponseDto (using Jackson to deserialize fields like type, name, steam_appid into record fields).
     * </p>
     *
     * @param appId                single app ID (e.g., "730")
     * @param steamApiFullResponse raw JsonNode from Steam API; must **not** be null
     * @return parsed DTO with appId, success from upper wrapper, and game data mapped from lower "data" node
     * @throws GameRecommenderException if app not found (null upper node), data missing (no "data" key), or business logic validation fails
     * @throws JsonProcessingException  if JSON structure is invalid and cannot be deserialized by Jackson
     */
    private SteamGameDetailsResponseDto parseJson(String appId, @NotNull JsonNode steamApiFullResponse) throws JsonProcessingException {
        log.debug("Steam api full response json: {}", steamApiFullResponse.asText());
        JsonNode upperGameDetailsNode = steamApiFullResponse.get(appId);
        if (upperGameDetailsNode == null) {
            throw new GameRecommenderException(ErrorType.STEAM_APP_DETAILS_NOT_FOUND, appId);
        }

        if (upperGameDetailsNode.has(SteamApiConstant.DATA)) {
            ObjectMapper objectMapper = new ObjectMapper();
            SteamGameDetailsResponseDto.SteamGameDataResponseDto steamGameDataResponseDto = objectMapper.treeToValue(upperGameDetailsNode.get(SteamApiConstant.DATA),
                    SteamGameDetailsResponseDto.SteamGameDataResponseDto.class);

            SteamGameDetailsResponseDto steamGameDetailsResponseDto = SteamGameDetailsResponseDto.builder()
                    .appId(appId)
                    .success(upperGameDetailsNode.at(SteamApiConstant.SUCCESS).asBoolean())
                    .steamGameDataResponseDto(steamGameDataResponseDto)
                    .build();

            log.debug("Mapped full steam Api response to steamGameDetailsResponseDto: {}", steamGameDetailsResponseDto);
            return steamGameDetailsResponseDto;
        } else {
            throw new GameRecommenderException(ErrorType.STEAM_DATA_IN_APP_DETAILS_NOT_FOUND, appId);
        }
    }
}