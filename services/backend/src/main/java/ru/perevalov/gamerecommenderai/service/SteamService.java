package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamClient;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

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
