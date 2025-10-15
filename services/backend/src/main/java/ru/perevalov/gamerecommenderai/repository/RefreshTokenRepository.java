package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, UUID> {
    Mono<RefreshToken> findByToken(String token); //TODO: Поставить индекс на поле в настоящей базе

    default Mono<RefreshToken> findByTokenOrThrow(String token) {
        return findByToken(token)
                .switchIfEmpty(Mono.error(new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID)));
    }
}
