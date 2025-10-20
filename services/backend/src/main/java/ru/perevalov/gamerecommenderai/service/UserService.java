package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
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
                // TODO: блокирующая заглушка, должен возвращаться Mono<User>. Переписать в PCAI-79
                .switchIfEmpty(Mono.error(() -> {
                    log.error("User with steam id {} was not found in system.", steamId);
                    return new GameRecommenderException(ErrorType.USER_NOT_FOUND, steamId);
                })).block();

        return user;
    }

    public User createIfNotExists(Long steamId) {
        return userRepository.findBySteamId(steamId)
                // TODO: блокирующая заглушка, должен возвращаться Mono<User>. Переписать в PCAI-79
                .switchIfEmpty(Mono.defer(() -> {
                    return userRepository.save(new User(steamId, UserRole.USER))
                            .doOnNext(user -> {
                                log.info("Created new user with steamId={} (userId={}).",
                                        user.getSteamId(), user.getId());
                                user.markAsExisting();
                            });
                })).block();
    }
}