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

    public Mono<User> findBySteamId(Long steamId) {
        return userRepository.findBySteamId(steamId)
                .switchIfEmpty(
                        Mono.error(new GameRecommenderException(ErrorType.USER_NOT_FOUND, steamId))
                )
                .doOnSuccess(user ->
                        log.info("User with steamId={} found in DB (userId={}).", user.getSteamId(), user.getId()))
                .doOnError(GameRecommenderException.class,
                        ex -> log.error("User with steam id {} was not found in system", steamId));
    }

    public Mono<User> createIfNotExists(Long steamId) {
        return userRepository.findBySteamId(steamId)
                .switchIfEmpty(Mono.defer(() ->
                        userRepository.save(new User(steamId, UserRole.USER))
                                .doOnSuccess(user ->
                                        log.info("Created new user with steamId={} (userId={}).",
                                                user.getSteamId(), user.getId())
                                )
                                .map(user -> {
                                    user.markAsExisting();
                                    return user;
                                })
                        )
                );
    }
}