package ru.perevalov.gamerecommenderai.it;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class LiquibaseChangelogTestIT extends BaseIT {

    @Autowired
    private DataSource dataSource;

    private static List<String> expectedTables = List.of(
            "users",
            "ai_agents",
            "refresh_tokens",
            "steam_profiles",
            "user_game_stats",
            "user_preferences",
            "api_logs"
    );

    @Test
    @DisplayName("Test liquibase migration")
    void givenLiquibaseMigration_afterMigrationCreation_shouldBeCreatedAllExceptedTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "%", "%", null)) {

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }

            expectedTables.forEach(expectedTable -> Assertions.assertThat(tables).contains(expectedTable));
        }
    }
}