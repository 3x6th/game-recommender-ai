package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.mapper.SteamProfileMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamUserDataUpdater {
    private final SteamService steamService;

    public Mono<Void> updateUserData(Long steamId) {
        log.info("🚀 Запуск обновления данных для {}", steamId);

        return updateSteamProfile(steamId)
                .then(updateUserGameStats(steamId))
                .doOnSuccess(v ->
                        log.info("All data has been updated for {}", steamId)
                )
                .doOnError(e ->
                        log.error("Error update for {}: {}", steamId, e.getMessage()
                        )
                );
    }

    public Mono<Void> updateSteamProfile(Long steamId) {
        log.debug("Updating SteamProfile, steamId: {}", steamId);

        return steamService.getPlayerSummaries(steamId)
                .flatMap(SteamProfileMapper::map)
                .flatMap(profile -> {

                    //сохранение в базу

                    log.info("SteamProfile saved in the database, steamId: {}",
                            steamId);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Error mapping SteamProfile, steamId: {}", steamId);
                    return Mono.empty();
                }))
                .doOnError(error ->
                        log.error("Error update SteamProfile, steamId: {} : {}", steamId, error.getMessage())
                )
                .then();
    }

    public Mono<Void> updateUserGameStats(Long steamId) {
        //логика обновления статистики по играм
        return Mono.empty();
    }

}
