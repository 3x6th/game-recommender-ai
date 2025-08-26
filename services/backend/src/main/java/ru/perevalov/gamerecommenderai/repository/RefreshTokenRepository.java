package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token); //TODO: Поставить индекс на поле в настоящей базе
    List<RefreshToken> findBySessionId(String sessionId);
}
