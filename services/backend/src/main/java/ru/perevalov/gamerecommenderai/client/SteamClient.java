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
 * Клиент для взаимодействия с Steam Web API.
 * <p>
 * Предоставляет методы для получения информации о пользователях и их играх через Steam API.
 * Клиент обрабатывает повторные попытки запросов и ведёт логирование запросов и ответов.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SteamClient {

    /**
     * Экземпляр WebClient, используемый для выполнения HTTP-запросов к Steam API.
     */
    private final WebClient steamWebClient;

    /**
     * Конфигурационные параметры Steam API, включающие базовый URL, API-ключ,
     * пути к эндпоинтам и настройки повторных попыток.
     */
    private final SteamProps props;

    /**
     * Получает информацию о пользователе по одному Steam ID.
     *
     * @param steamId Steam ID пользователя, информацию о котором нужно получить
     * @return {@link SteamPlayerResponse}, содержащий данные о пользователе
     * @throws RuntimeException если запрос к Steam API завершился неудачно
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
     * Получает список игр, принадлежащих пользователю.
     *
     * @param steamId                Steam ID пользователя
     * @param includeAppInfo         указывает, нужно ли включать дополнительную информацию об играх
     * @param includePlayedFreeGames указывает, нужно ли включать бесплатные игры, в которые играл пользователь
     * @return {@link SteamOwnedGamesResponse}, содержащий список игр пользователя
     * @throws RuntimeException если запрос к Steam API завершился неудачно
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
