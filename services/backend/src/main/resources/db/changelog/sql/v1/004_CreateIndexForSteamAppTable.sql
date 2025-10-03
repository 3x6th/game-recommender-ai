CREATE INDEX idx_steam_apps_name ON game_recommender.steam_apps (name);
CREATE INDEX idx_steam_apps_name_lower ON game_recommender.steam_apps (LOWER(name));