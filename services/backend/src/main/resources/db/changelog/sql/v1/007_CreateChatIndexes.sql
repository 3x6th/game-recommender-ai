CREATE INDEX chats_user_updated_idx
    ON game_recommender.chats (user_id, updated_at DESC);

CREATE INDEX chats_session_updated_idx
    ON game_recommender.chats (session_id, updated_at DESC);

CREATE INDEX chat_messages_chat_created_idx
    ON game_recommender.chat_messages (chat_id, created_at DESC);

CREATE INDEX chat_messages_chat_clientreq_idx
    ON game_recommender.chat_messages (chat_id, client_request_id);
