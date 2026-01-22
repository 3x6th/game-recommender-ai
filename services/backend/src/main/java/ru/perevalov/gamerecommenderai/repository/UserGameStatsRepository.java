package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;

import java.util.UUID;

@Repository
public interface UserGameStatsRepository extends ReactiveCrudRepository<UserGameStats, UUID> {
    Mono<UserGameStats> findByUserId(UUID userId);
}

