package ru.perevalov.gamerecommenderai.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.service.GameService;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameScheduler {
    private final GameService gameService;

    @Value("${app.scheduler.update-steam-apps.cron}")
    private String updateCron;

    @Scheduled(cron = "#{@updateCron}")
    public void updateGames() {
        log.info("Scheduled update started");
        try {
            gameService.updateGames();
            log.info("Scheduled update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled update failed due to an error", e);
            throw new GameRecommenderException(ErrorType.SCHEDULER_UPDATE_EXECUTION_ERROR);
        }
    }
}
