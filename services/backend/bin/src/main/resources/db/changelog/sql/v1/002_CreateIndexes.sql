CREATE INDEX idx_users_steam_id ON game_recommender.users (steam_id);
CREATE INDEX idx_api_logs_user_id ON game_recommender.api_logs (user_id);
CREATE INDEX idx_api_logs_ai_agent_id ON game_recommender.api_logs (ai_agent_id);