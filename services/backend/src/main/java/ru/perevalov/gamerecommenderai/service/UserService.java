package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User findBySteamId(Long steamId) {
        User user = userRepository.findBySteamId(steamId)
                .orElseThrow(() -> {
                    log.error("User with steam id {} was not found in system.", steamId);
                    return new GameRecommenderException(ErrorType.USER_NOT_FOUND, steamId);
                });
        log.info("User with steamId={} found in DB (userId={}).", user.getSteamId(), user.getId());
        return user;
    }

    public User createIfNotExists(Long steamId) {
        return userRepository.findBySteamId(steamId)
                .orElseGet(() -> {
                            User saved = userRepository.save(new User(steamId, UserRole.USER));
                            log.info("Created new user with steamId={} (userId={}).", saved.getSteamId(), saved.getId());
                            return saved;
                        }
                );
    }
}