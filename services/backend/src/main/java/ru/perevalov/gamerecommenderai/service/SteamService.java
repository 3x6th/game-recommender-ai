package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamClient;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

/**
 * Сервис для работы с информацией о пользователях Steam и их играх.
 * <p>
 * Использует {@link SteamClient} для обращения к Steam Web API.
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

    private final SteamClient steamClient;

    public SteamPlayerResponse getPlayerSummaries(String steamId) {
        return steamClient.fetchPlayerSummaries(steamId);
    }

    public SteamOwnedGamesResponse getOwnedGames(String steamId,
                                                 boolean includeAppInfo,
                                                 boolean includePlayedFreeGames) {
        return steamClient.fetchOwnedGames(steamId, includeAppInfo, includePlayedFreeGames);
    }
}
