-- PCAI-122: pg_trgm + GIN индекс на LOWER(name) для SearchGames gRPC tool.
-- Без этого ILIKE '%query%' превращается в seq scan по ~70k записей на каждый вызов.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE INDEX IF NOT EXISTS idx_steam_apps_name_trgm
    ON game_recommender.steam_apps
    USING gin (LOWER(name) gin_trgm_ops);
