package ru.perevalov.gamerecommenderai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.perevalov.gamerecommenderai.dto.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.SteamPlayerResponse;

@FeignClient(name = "steamClient", url = "${steam.baseUrl}")
public interface SteamClient {

    @GetMapping("/ISteamUser/GetPlayerSummaries/v0002/")
    SteamPlayerResponse getPlayerSummaries(
            @RequestParam("key") String apiKey,
            @RequestParam("steamids") String steamIds
    );

    @GetMapping("/IPlayerService/GetOwnedGames/v0001/")
    SteamOwnedGamesResponse getOwnedGames(
            @RequestParam("key") String apiKey,
            @RequestParam("steamid") String steamId,
            @RequestParam(value = "include_appinfo", defaultValue = "true") boolean includeAppInfo,
            @RequestParam(value = "include_played_free_games", defaultValue = "true") boolean includePlayedFreeGames
    );
}
