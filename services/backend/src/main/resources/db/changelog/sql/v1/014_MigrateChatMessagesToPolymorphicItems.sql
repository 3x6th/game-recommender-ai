-- PCAI-141 follow-up: уносим reasoning и игровые карточки в полиморфный
-- payload.items[] с дискриминатором kind.
--
-- До этого ассистентские сообщения шли с type=mixed и payload =
--   { text, items, reasoning?, extra? }
-- Теперь — type=cards и payload = { items: [{kind:"reasoning",text:...}, {kind:"game",...}] }.
-- Поля text/reasoning/extra и сам тип mixed удаляются.
--
-- ВАЖНО: jsonb-оператор `?` (key existence) НЕ используется напрямую — JDBC
-- интерпретирует его как параметр-плейсхолдер и Liquibase падает на
-- "syntax error at or near $1". Везде используется эквивалентная функция
-- jsonb_exists(jsonb, text).

-- 1) Конвертим mixed-сообщения: собираем новые items из reasoning + старого items[].
UPDATE game_recommender.chat_messages
SET meta = jsonb_set(
        jsonb_set(
            meta,
            '{type}',
            '"cards"'::jsonb,
            true
        ),
        '{payload}',
        jsonb_build_object(
            'items',
            COALESCE(
                CASE
                    WHEN meta->'payload'->>'reasoning' IS NOT NULL
                         AND length(meta->'payload'->>'reasoning') > 0
                    THEN jsonb_build_array(jsonb_build_object(
                        'kind', 'reasoning',
                        'text', meta->'payload'->>'reasoning'
                    ))
                    ELSE '[]'::jsonb
                END
                || COALESCE(
                    (
                        SELECT jsonb_agg(elem || jsonb_build_object('kind', 'game'))
                        FROM jsonb_array_elements(meta->'payload'->'items') AS elem
                    ),
                    '[]'::jsonb
                ),
                '[]'::jsonb
            )
        ),
        true
    )
WHERE meta->>'type' = 'mixed';

-- 2) В старых cards-сообщениях (если есть) тоже подмешиваем kind="game" в каждый item
--    и убираем поле reasoning, если оно было.
UPDATE game_recommender.chat_messages
SET meta = jsonb_set(
        meta,
        '{payload}',
        jsonb_build_object(
            'items',
            COALESCE(
                CASE
                    WHEN meta->'payload'->>'reasoning' IS NOT NULL
                         AND length(meta->'payload'->>'reasoning') > 0
                    THEN jsonb_build_array(jsonb_build_object(
                        'kind', 'reasoning',
                        'text', meta->'payload'->>'reasoning'
                    ))
                    ELSE '[]'::jsonb
                END
                || COALESCE(
                    (
                        SELECT jsonb_agg(
                            CASE
                                WHEN jsonb_exists(elem, 'kind') THEN elem
                                ELSE elem || jsonb_build_object('kind', 'game')
                            END
                        )
                        FROM jsonb_array_elements(meta->'payload'->'items') AS elem
                    ),
                    '[]'::jsonb
                ),
                '[]'::jsonb
            )
        ),
        true
    )
WHERE meta->>'type' = 'cards'
  AND (
        jsonb_exists(meta->'payload', 'reasoning')
        OR EXISTS (
            SELECT 1
            FROM jsonb_array_elements(meta->'payload'->'items') AS elem
            WHERE NOT jsonb_exists(elem, 'kind')
        )
      );

-- 3) Чистим content для cards-сообщений: всё содержимое теперь в items[].
UPDATE game_recommender.chat_messages
SET content = ''
WHERE role = 'ASSISTANT'
  AND meta->>'type' = 'cards'
  AND content IS NOT NULL
  AND content <> '';
