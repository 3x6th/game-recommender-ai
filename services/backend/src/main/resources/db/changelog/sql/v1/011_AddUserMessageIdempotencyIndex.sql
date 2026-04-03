CREATE UNIQUE INDEX ux_chat_messages_user_chat_id_client_request_id
    ON game_recommender.chat_messages (chat_id, client_request_id)
    WHERE role = 'USER' AND client_request_id IS NOT NULL;
