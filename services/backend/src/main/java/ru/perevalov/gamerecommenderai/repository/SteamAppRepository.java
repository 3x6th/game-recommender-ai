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

    /**
     * Fuzzy-поиск по подстроке имени (case-insensitive). Использует trigram GIN-индекс
     * {@code idx_steam_apps_name_trgm} (Liquibase 011), сортирует по trigram-сходству.
     * <p>
     * Параметр {@code pattern} должен быть уже приведён к нижнему регистру и обёрнут в
     * {@code %...%} вызывающей стороной.
     *
     * @param pattern ILIKE-шаблон, например {@code %counter%}
     * @param query   оригинальный lowercased запрос без процентов — для {@code similarity()}
     * @param limit   максимальное количество возвращаемых записей
     */
    @Query("""
            SELECT * FROM game_recommender.steam_apps
            WHERE LOWER(name) LIKE :pattern
            ORDER BY similarity(LOWER(name), :query) DESC, LENGTH(name) ASC
            LIMIT :limit
            """)
    Flux<SteamAppEntity> searchByNameLike(@Param("pattern") String pattern,
                                          @Param("query") String query,
                                          @Param("limit") int limit);
}
