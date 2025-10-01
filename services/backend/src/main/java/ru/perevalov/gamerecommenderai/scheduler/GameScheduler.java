package ru.perevalov.gamerecommenderai.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.springframework.scheduling.annotation.Scheduled;
import ru.perevalov.gamerecommenderai.service.GameService;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameScheduler {
    private final GameService gameService;

    @Scheduled(cron = "0 0 2 ? * SUN")
    public void updateSteamApps() {
        log.info("Scheduled update started");
        gameService.updateSteamApps();
        log.info("Scheduled update initiated");
    }
}
