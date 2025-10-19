package ru.perevalov.gamerecommenderai.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;

import java.util.List;

@Component
@Slf4j
public class SteamProfileMapper {

    public static Mono<SteamProfile> map(SteamPlayerResponse response) {

        if (response == null || response.getResponse() == null ||
                response.getResponse().getPlayers() == null ||
                response.getResponse().getPlayers().isEmpty()) {
            log.warn("The Steam profile does not contain any players — it is possible that the SteamID is incorrect");
            return Mono.empty();
        }

        return Mono.justOrEmpty(response)
                .map(SteamPlayerResponse::getResponse)
                .map(SteamPlayerResponse.Response::getPlayers)
                .filter(players -> !players.isEmpty())
                .map(List::getFirst)
                .map(player -> {
                    SteamProfile profile = new SteamProfile();
                    profile.setSteamId(player.getSteamId());
                    profile.setPersonaName(player.getPersonaName());
                    profile.setAvatarFull(player.getAvatarFull());

                    log.info("Successfully swapped the profile for {}", player.getSteamId());

                    return profile;
                });
    }
}
