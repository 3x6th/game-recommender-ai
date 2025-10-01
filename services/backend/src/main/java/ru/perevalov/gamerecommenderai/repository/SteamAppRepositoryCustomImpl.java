package ru.perevalov.gamerecommenderai.repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@Transactional
public class SteamAppRepositoryCustomImpl implements SteamAppRepositoryCustom {
    private final JdbcTemplate jdbcTemplate;

    public SteamAppRepositoryCustomImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void batchInsert(List<SteamAppEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO game_recommender.steam_apps (appid, name) VALUES (?, ?) " +
                "ON CONFLICT (appid) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SteamAppEntity entity = entities.get(i);
                ps.setLong(1, entity.getAppid());
                ps.setString(2, entity.getName());
            }

            @Override
            public int getBatchSize() {
                return entities.size();  // Все entities отправляются в одном batch
            }
        });
    }
}