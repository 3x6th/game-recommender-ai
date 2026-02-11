package ru.perevalov.gamerecommenderai.repository;

import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.util.List;

public interface SteamAppRepositoryCustom {
    Mono<Void> batchInsert(List<SteamAppEntity> entities);

}
