package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamUserClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;

/**
 * Сервис для работы с информацией о пользователях Steam и их играх.
 * <p>
 * Использует {@link SteamUserClient} для обращения к Steam Web API.
 * Предоставляет методы:
 * <ul>
 *     <li>getPlayerSummaries — получить информацию о пользователях по Steam ID</li>
 *     <li>getOwnedGames — получить список игр пользователя с деталями</li>
 * </ul>
 * <p>
 * Все методы возвращают объекты-модели API Steam и обрабатывают пустые или приватные профили.
 */
@Service
@RequiredArgsConstructor
public class SteamService {

    private final SteamUserClient steamUserClient;

    public SteamPlayerResponse getPlayerSummaries(String steamId) {
        return steamUserClient.fetchPlayerSummaries(steamId);
    }

    public SteamOwnedGamesResponse getOwnedGames(String steamId,
                                                 boolean includeAppInfo,
                                                 boolean includePlayedFreeGames) {
        return steamUserClient.fetchOwnedGames(steamId, includeAppInfo, includePlayedFreeGames);
    }
}
