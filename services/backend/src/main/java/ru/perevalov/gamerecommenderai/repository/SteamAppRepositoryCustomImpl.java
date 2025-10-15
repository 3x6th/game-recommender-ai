package ru.perevalov.gamerecommenderai.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.util.List;

@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class SteamAppRepositoryCustomImpl implements SteamAppRepositoryCustom {
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> batchInsert(List<SteamAppEntity> entities) {
        if (entities.isEmpty()) {
            return Mono.empty();
        }

        String insertOnConflictUpdateSql = """
                    INSERT INTO game_recommender.steam_apps (appid, name)
                    VALUES ($1, $2)
                    ON CONFLICT (appid) DO UPDATE SET name = EXCLUDED.name
                """;

        return Flux.fromIterable(entities)
                .buffer(300)
                .flatMap(batch ->
                        Flux.fromIterable(batch)
                                .flatMap(entity -> databaseClient.sql(insertOnConflictUpdateSql)
                                        .bind(0, entity.getAppid())
                                        .bind(1, entity.getName())
                                        .fetch()
                                        .rowsUpdated()
                                ).then()
                ).then()
                .onErrorMap(exception -> {
                    log.error("Error batch inserting games to database", exception);
                    throw new GameRecommenderException(ErrorType.DATABASE_BATCH_INSERT_ERROR, exception);
                });
    }
}