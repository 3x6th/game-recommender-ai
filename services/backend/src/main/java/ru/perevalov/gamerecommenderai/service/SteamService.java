package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamClient;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SteamService {

    private final SteamClient steamClient;

    @Value("${steam.apiKey}")
    private String apiKey;

    //для метода SteamClient.getPlayerSummaries()
    public List<SteamPlayerResponse.Player> getPlayers(String steamIds) {
        var resp = steamClient.getPlayerSummaries(apiKey, steamIds);

        //если профиль приватный или Steam вернул пустой объект
        return resp != null && resp.getResponse() != null
                ? resp.getResponse().getPlayers()
                : List.of();
    }

    public List<SteamOwnedGamesResponse.Game> getOwnedGames(String steamId) {
        var resp = steamClient.getOwnedGames(apiKey, steamId, true,true);

        // Если профиль приватный или Steam вернул пустой объект
        return resp != null && resp.getResponse() != null
                ? resp.getResponse().getGames()
                : List.of();
    }
}
