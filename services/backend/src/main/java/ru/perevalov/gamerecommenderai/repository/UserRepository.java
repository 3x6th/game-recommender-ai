package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.User;

import java.util.UUID;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, UUID> {
    Mono<User> findBySteamId(Long steamId);
}
