package ru.perevalov.gamerecommenderai.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.util.List;

@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class SteamAppRepositoryCustomImpl implements SteamAppRepositoryCustom {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    @Override
    public void batchInsert(List<SteamAppEntity> entities) {
        if (entities.isEmpty()) return;

        String insertOnConflictUpdateSql = "INSERT INTO game_recommender.steam_apps (appid, name) " +
                "VALUES (?, ?) " +
                "ON CONFLICT (appid) DO UPDATE SET name = EXCLUDED.name";
        try {
            jdbcTemplate.batchUpdate(
                    insertOnConflictUpdateSql,
                    entities,
                    entities.size(),
                    (ps, entity) -> {
                        ps.setLong(1, entity.getAppid());
                        ps.setString(2, entity.getName());
                    }
            );
        } catch (Exception e) {
            log.error("Error batch inserting games to database", e);
            throw new GameRecommenderException(ErrorType.DATABASE_BATCH_INSERT_ERROR, e);
        }
    }
}