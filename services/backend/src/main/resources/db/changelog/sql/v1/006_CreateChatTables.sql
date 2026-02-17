CREATE TABLE game_recommender.chats
(
    id         UUID PRIMARY KEY,
    user_id    UUID NULL,
    session_id VARCHAR(64) NULL,
    ai_agent_id UUID NULL,
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chats_status_chk CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE game_recommender.chat_messages
(
    id                UUID PRIMARY KEY,
    chat_id           UUID NOT NULL
        REFERENCES game_recommender.chats (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL,
    content           TEXT NULL,
    meta              JSONB NOT NULL DEFAULT '{"schemaVersion":1}'::jsonb,
    client_request_id UUID NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_messages_role_chk CHECK (role IN ('USER','ASSISTANT','SYSTEM','TOOL')),
    CONSTRAINT chat_messages_meta_obj_chk CHECK (jsonb_typeof(meta) = 'object')
);

ALTER TABLE game_recommender.chats
    ADD CONSTRAINT fk_chats_users_id
        FOREIGN KEY (user_id)
            REFERENCES game_recommender.users (id)
            ON DELETE SET NULL;

ALTER TABLE game_recommender.chats
    ADD CONSTRAINT fk_chats_ai_agents_id
        FOREIGN KEY (ai_agent_id)
            REFERENCES game_recommender.ai_agents (id)
            ON DELETE SET NULL;

