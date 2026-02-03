package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;

import java.util.UUID;

@Repository
public interface SteamProfileRepository extends ReactiveCrudRepository<SteamProfile, UUID> {
    Mono<SteamProfile> findByUserId(UUID userId);
}

