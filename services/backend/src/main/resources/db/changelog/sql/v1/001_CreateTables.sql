CREATE TYPE game_recommender.role_enum AS ENUM ('GUEST', 'USER');

CREATE TABLE game_recommender.users
(
    id         UUID PRIMARY KEY,
    steam_id   BIGINT                     NOT NULL UNIQUE,
    is_active  BOOLEAN                    NOT NULL,
    created_at TIMESTAMP(6)               NOT NULL,
    updated_at TIMESTAMP(6)               NOT NULL,
    role       game_recommender.role_enum NOT NULL
);

CREATE TABLE game_recommender.ai_agents
(
    id         UUID PRIMARY KEY,
    ai_name    VARCHAR(50)  NOT NULL,
    model_name VARCHAR(50),
    is_active  BOOLEAN      NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE game_recommender.refresh_tokens
(
    id            UUID PRIMARY KEY,
    token         VARCHAR(2048) NOT NULL UNIQUE,
    session_id    VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP(6)  NOT NULL,
    updated_at    TIMESTAMP(6)  NOT NULL
);

CREATE TABLE game_recommender.steam_profiles
(
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL UNIQUE,
    profile_img   VARCHAR(255),
    profile_url   VARCHAR(255),
    steam_created INTEGER      NOT NULL UNIQUE,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL
);

ALTER TABLE game_recommender.steam_profiles
    ADD CONSTRAINT fk_steam_profiles_users_id
        FOREIGN KEY (user_id)
            REFERENCES game_recommender.users (id)
            ON DELETE CASCADE;

CREATE TABLE game_recommender.user_game_stats
(
    id                            UUID PRIMARY KEY,
    steam_id                      BIGINT       NOT NULL,
    user_id                       UUID         NOT NULL UNIQUE,
    last_playtime                 INTEGER,
    favorite_genre_count          INTEGER,
    favorite_genre_hours          INTEGER,
    last_played_game_id           BIGINT,
    favorite_genre                VARCHAR(100),
    last_played_game_name         VARCHAR(255),
    most_played_game_hours        INTEGER,
    most_played_game_id           BIGINT,
    most_played_game_name         VARCHAR(255),
    total_games_owned             INTEGER,
    total_playtime_forever        INTEGER,
    total_playtime_last_two_weeks INTEGER,
    created_at                    TIMESTAMP(6) NOT NULL,
    updated_at                    TIMESTAMP(6) NOT NULL
);

ALTER TABLE game_recommender.user_game_stats
    ADD CONSTRAINT fk_user_game_stats_users_id
        FOREIGN KEY (user_id)
            REFERENCES game_recommender.users (id)
            ON DELETE CASCADE;

CREATE TABLE game_recommender.user_preferences
(
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL,
    ai_agent_id  UUID,
    game_id      BIGINT,
    game_name    VARCHAR(255),
    reasoning    TEXT,
    generated_at TIMESTAMP(6),
    tags         JSONB,
    created_at   TIMESTAMP(6) NOT NULL,
    updated_at   TIMESTAMP(6) NOT NULL
);

ALTER TABLE game_recommender.user_preferences
    ADD CONSTRAINT fk_user_preferences_users_id
        FOREIGN KEY (user_id)
            REFERENCES game_recommender.users (id)
            ON DELETE CASCADE;

ALTER TABLE game_recommender.user_preferences
    ADD CONSTRAINT fk_user_preferences_ai_agents_id
        FOREIGN KEY (ai_agent_id)
            REFERENCES game_recommender.ai_agents (id)
            ON DELETE SET NULL;

CREATE TYPE game_recommender.log_type_enum AS ENUM ('REQUEST', 'RESPONSE');

CREATE TABLE game_recommender.api_logs
(
    id               UUID PRIMARY KEY,
    user_id          UUID                           NOT NULL,
    ai_agent_id      UUID                           NOT NULL,
    endpoint         VARCHAR(100)                   NOT NULL,
    timestamp        TIMESTAMP(6)                   NOT NULL,
    log_type         game_recommender.log_type_enum NOT NULL,
    message          TEXT                           NOT NULL,
    status_code      SMALLINT,
    response_time_ms INTEGER,
    created_at       TIMESTAMP(6)                   NOT NULL,
    updated_at       TIMESTAMP(6)                   NOT NULL
);

ALTER TABLE game_recommender.api_logs
    ADD CONSTRAINT fk_api_logs_users_id
        FOREIGN KEY (user_id)
            REFERENCES game_recommender.users (id)
            ON DELETE CASCADE;

ALTER TABLE game_recommender.api_logs
    ADD CONSTRAINT fk_api_logs_ai_agents_id
        FOREIGN KEY (ai_agent_id)
            REFERENCES game_recommender.ai_agents (id)
            ON DELETE SET NULL;