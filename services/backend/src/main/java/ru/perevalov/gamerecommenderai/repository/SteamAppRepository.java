package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SteamAppRepository extends ReactiveCrudRepository<SteamAppEntity, UUID>, SteamAppRepositoryCustom {

    @Query("SELECT * FROM game_recommender.steam_apps WHERE LOWER(steam_apps.name) IN :names")
    Flux<SteamAppEntity> findByLowerNameIn(@Param("names") List<String> names);
}
