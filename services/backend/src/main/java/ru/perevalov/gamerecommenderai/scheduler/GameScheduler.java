package ru.perevalov.gamerecommenderai.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.service.GameService;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameScheduler {
    private final GameService gameService;

    @Scheduled(cron = "${app.scheduler.update-steam-apps.cron}")
    public void updateGames() {
        log.info("Scheduled update triggered at {}", LocalDateTime.now());
        try {
            gameService.updateGames();
            log.info("Scheduled update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled update failed due to an error", e);
            throw new GameRecommenderException(ErrorType.SCHEDULER_UPDATE_EXECUTION_ERROR);
        }
    }
}
