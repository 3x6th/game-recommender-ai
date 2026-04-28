-- PCAI-141: симметрия meta-envelope для USER и ASSISTANT.
--
-- В 006 у chat_messages.meta дефолт '{"schemaVersion":1}'::jsonb. Ассистентские
-- сообщения сохраняются через MessageMetaFactory с полным envelope, но в БД
-- могут быть исторические записи (преимущественно USER), у которых meta —
-- "огрызок" без type/payload. Это ломает паритет ответа /proceed и
-- GET /chats/{chatId}/messages, поэтому делаем одноразовый backfill,
-- подкручиваем дефолт колонки и фиксируем инвариант через CHECK.

-- 1) Backfill: где meta не содержит type — собираем минимальный reply-envelope
--    из текущего content (или пустой строки, если NULL).
UPDATE game_recommender.chat_messages
SET meta = jsonb_build_object(
        'schemaVersion', 1,
        'type', 'reply',
        'payload', jsonb_build_object('text', COALESCE(content, ''))
    )
WHERE meta->>'type' IS NULL;

-- 2) Подтягиваем дефолт под новый формат: если кто-то когда-то сделает INSERT
--    без явного meta, в БД ляжет валидный envelope, а не огрызок.
ALTER TABLE game_recommender.chat_messages
    ALTER COLUMN meta SET DEFAULT '{"schemaVersion":1,"type":"reply","payload":{}}'::jsonb;

-- 3) Жёсткий чек инварианта: meta.type обязан быть непустой строкой.
--    Дублирует MessageMetaValidator на уровне БД — последняя линия обороны
--    против неконсистентной записи через прямой SQL.
ALTER TABLE game_recommender.chat_messages
    ADD CONSTRAINT chat_messages_meta_type_chk
        CHECK (jsonb_typeof(meta -> 'type') = 'string'
               AND length(meta ->> 'type') > 0);
