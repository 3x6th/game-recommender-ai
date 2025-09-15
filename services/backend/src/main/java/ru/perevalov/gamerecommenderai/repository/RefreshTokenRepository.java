package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token); //TODO: Поставить индекс на поле в настоящей базе

    List<RefreshToken> findBySessionId(String sessionId);

    default RefreshToken findByTokenOrThrow(String token) {
        return findByToken(token).orElseThrow(() -> new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID));
    }
}
