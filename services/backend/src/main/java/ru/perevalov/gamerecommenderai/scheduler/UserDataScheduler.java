package ru.perevalov.gamerecommenderai.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.model.UserRole;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.service.SteamUserDataService;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserDataScheduler {

    private final UserRepository userRepository;
    private final SteamUserDataService steamUserDataService;

    /**
     * Periodically refresh Steam profile and game stats for users already linked to Steam.
     * <p>
     * Runs in background and is meant to keep DB + cache in sync with Steam changes.
     */
    @Scheduled(cron = "${app.scheduler.update-user-data.cron}")
    public void refreshUserData() {
        log.info("Scheduled user data refresh triggered at {}", LocalDateTime.now());
        try {
            userRepository.findAll()
                    .filter(user -> user.getRole() == UserRole.USER && user.getSteamId() != null)
                    // Keep concurrency low because Steam API has strict rate limits.
                    .flatMap(steamUserDataService::syncUserData, 2)
                    .then()
                    .block();
            log.info("Scheduled user data refresh completed successfully");
        } catch (Exception e) {
            log.error("Scheduled user data refresh failed due to an error", e);
        }
    }
}

