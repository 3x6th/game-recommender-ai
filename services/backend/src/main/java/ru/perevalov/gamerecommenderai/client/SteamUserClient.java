package ru.perevalov.gamerecommenderai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.client.props.SteamUserProps;
import ru.perevalov.gamerecommenderai.constant.SteamApiConstant;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.util.UrlHelper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

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
public class SteamUserClient {

    /**
     * Экземпляр WebClient, используемый для выполнения HTTP-запросов к Steam API.
     */
    private final WebClient steamWebClient;

    /**
     * Конфигурационные параметры Steam API, включающие базовый URL, API-ключ,
     * пути к эндпоинтам и настройки повторных попыток.
     */
    private final SteamUserProps props;

//    /**
//     * Получает информацию о пользователе по одному Steam ID.
//     *
//     * @param steamId Steam ID пользователя, информацию о котором нужно получить
//     * @return {@link SteamPlayerResponse}, содержащий данные о пользователе
//     * @throws RuntimeException если запрос к Steam API завершился неудачно
//     */
//    public SteamPlayerResponse fetchPlayerSummaries(String steamId) {
//
//        try {
//            log.debug("Fetching player summary for steamId={}", steamId);
//
//            URI uri = UrlHelper.buildUri(
//                    props.scheme(),
//                    props.host(),
//                    props.getPlayerSummariesPath(),
//                    Map.of(
//                            SteamApiConstant.KEY, props.apiKey(),
//                            SteamApiConstant.STEAMIDS, steamId
//                    )
//            );
//
//            SteamPlayerResponse response = steamWebClient.get()
//                    .uri(uri)
//                    .retrieve()
//                    .bodyToMono(SteamPlayerResponse.class)
//                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
//                    .block();
//
//            log.debug("Player summary response: {}", response);
//
//            return response;
//        } catch (Exception e) {
//            log.error("Error fetching player summaries for steamId={}", steamId, e);
//            throw new GameRecommenderException(ErrorType.STEAM_API_PLAYER_SUMMARY_ERROR, steamId);
//        }
//    }
    /**
     * Асинхронно получает информацию о пользователе по Steam ID.
     * Возвращает Mono, который начинает выполнение при подписке и эмитит результат когда он готов.
     *
     * @param steamId – Steam ID пользователя, информацию о котором нужно получить
     * @return Mono<SteamPlayerResponse>, который эмитит данные о пользователе при успешном запросе,
     *         или ошибку GameRecommenderException если запрос не удался
     *
     * @apiNote Для активации запроса необходимо выполнить подписку (subscribe).
     *          Метод не блокирует текущий поток.
     *
     * @example
     * // Использование:
     * fetchPlayerSummariesReactive("76561197960287930")
     *     .subscribe(
     *         response -> log.info("Данные получены: {}", response),
     *         error -> log.error("Ошибка: {}", error),
     *         () -> log.info("Запрос завершен")
     *     );
     */
    public Mono<SteamPlayerResponse> fetchPlayerSummaries(String steamId) {
        log.debug("Fetching player summary for steamId={}", steamId);

        URI uri = UrlHelper.buildUri(
                props.scheme(),
                props.host(),
                props.getPlayerSummariesPath(),
                Map.of(
                        SteamApiConstant.KEY, props.apiKey(),
                        SteamApiConstant.STEAMIDS, steamId
                )
        );

        return steamWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(SteamPlayerResponse.class)
                .retryWhen(Retry.fixedDelay(
                                props.retryAttempts(),
                                Duration.ofSeconds(props.retryDelaySeconds())
                        )
                )
                .doOnNext(response ->
                        log.debug("Player summary response: {}", response)
                )
                .doOnError(error -> {
                    log.error("Error fetching player summary for steamId={}", steamId, error);
                    throw new GameRecommenderException(ErrorType.STEAM_API_PLAYER_SUMMARY_ERROR, steamId);
                });
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

            URI uri = UrlHelper.buildUri(
                    props.scheme(),
                    props.host(),
                    props.getOwnedGamesPath(),
                    Map.of(
                            SteamApiConstant.KEY, props.apiKey(),
                            SteamApiConstant.STEAMID, steamId,
                            SteamApiConstant.INCLUDE_APPINFO, includeAppInfo,
                            SteamApiConstant.INCLUDE_PLAYED_FREE_GAMES, includePlayedFreeGames
                    )
            );

            SteamOwnedGamesResponse response = steamWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(SteamOwnedGamesResponse.class)
                    .retryWhen(Retry.fixedDelay(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds())))
                    .block();

            log.debug("Owned games response: {}", response);

            return response;
        } catch (Exception e) {
            log.error("Error fetching owned games for steamId={}", steamId, e);
            throw new GameRecommenderException(ErrorType.STEAM_API_FETCH_OWNED_GAMES_ERROR, steamId);
        }
    }

}
