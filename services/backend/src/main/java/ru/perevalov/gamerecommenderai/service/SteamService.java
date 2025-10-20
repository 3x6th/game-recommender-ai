package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.SteamUserClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;

/**
 * Сервис для работы с информацией о пользователях Steam и их играх.
 * <p>
 * Использует {@link SteamUserClient} для обращения к Steam Web API.
 * Предоставляет методы:
 * <ul>
 *     <li>getPlayerSummariesReactive — получить информацию о пользователе по Steam ID</li>
 *     <li>getOwnedGames — получить список игр пользователя с деталями</li>
 * </ul>
 * <p>
 * Все методы возвращают объекты-модели API Steam и обрабатывают пустые или приватные профили.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SteamService {

    private final SteamUserClient steamUserClient;

//    public SteamPlayerResponse getPlayerSummaries(String steamId) {
//        return steamUserClient.fetchPlayerSummaries(steamId);
//    }

    public Mono<SteamPlayerResponse> getPlayerSummaries(Long steamId) {
        log.debug("Reactive service: request data for {}", steamId);

        return steamUserClient.fetchPlayerSummaries(String.valueOf(steamId))
                .doOnNext(response ->
                        log.debug("The service received data for {}", steamId)
                )
                .doOnError(error ->
                        log.error("Error in the service for {}: {}", steamId, error.getMessage())
                );
    }

    public SteamOwnedGamesResponse getOwnedGames(String steamId,
                                                 boolean includeAppInfo,
                                                 boolean includePlayedFreeGames) {
        return steamUserClient.fetchOwnedGames(steamId, includeAppInfo, includePlayedFreeGames);
    }
}
