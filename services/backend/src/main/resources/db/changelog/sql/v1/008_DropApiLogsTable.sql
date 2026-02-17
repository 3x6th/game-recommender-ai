ALTER TABLE IF EXISTS game_recommender.api_logs
    DROP CONSTRAINT IF EXISTS fk_api_logs_users_id;

ALTER TABLE IF EXISTS game_recommender.api_logs
    DROP CONSTRAINT IF EXISTS fk_api_logs_ai_agents_id;

DROP TABLE IF EXISTS game_recommender.api_logs;
