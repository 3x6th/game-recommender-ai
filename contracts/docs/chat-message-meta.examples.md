# Chat Message Meta Examples

This document fixes the canonical shape of `chat_messages.meta` and provides examples.
For REST API contract and pagination rules, see `./api-contract.md`.

`meta.type` is **lowercase** (`reply`, `cards`, `status`, `error`, `tool_call`,
`tool_result`) — matches `MessageMetaType.wireName()` in code. `mixed` was retired
together with `payload.text` / `payload.reasoning` / `payload.extra`: see PCAI-141
follow-up.

## Canonical Envelope

Every meta object MUST follow the same envelope:

```json
{
  "schemaVersion": 1,
  "type": "reply|cards|status|error|...",
  "payload": {},
  "trace": {
    "requestId": "optional",
    "runId": "optional"
  }
}
```

Rules:
- `content` is the bubble text shown in chat UI. For `cards` it is intentionally empty
  — the bubble is rendered fully from `payload.items[]`.
- `schemaVersion` is an integer and `>= 1`.
- `type` is a non-empty string.
- `payload` is required (can be `{}`); usually an object.
- `trace` is optional; if present it must be an object with optional string fields
  `requestId` and `runId`.
- `meta` is **required for every role**, including USER. USER messages are stored
  with `meta.type = "reply"` and `payload.text = content`. This keeps chat history
  symmetric (one DTO, one (de)serializer for both sides of the conversation).

## USER message (canonical)

```json
{
  "messageId": "8e1c4a44-7b39-4f4a-9b23-9b1cda3a0a01",
  "role": "USER",
  "content": "Хочу что-то лайтовое и не шутер, гонки",
  "meta": {
    "schemaVersion": 1,
    "type": "reply",
    "payload": { "text": "Хочу что-то лайтовое и не шутер, гонки" }
  },
  "createdAt": "2026-04-26T18:53:00Z"
}
```

## Cards (Canonical Form)

`meta.type = "cards"` — основной тип ответа ассистента. `content` пустой —
всё содержимое в `payload.items[]`. `items[]` — полиморфный массив с
дискриминатором `kind`:

- `kind: "reasoning"` — метакомментарий «почему именно этот набор» (поля: `text`).
- `kind: "game"` — карточка игры (поля: `title`, `genre`, `description`,
  `whyRecommended`, `platforms[]`, опц. `rating`, `releaseYear`, `tags`,
  `matchScore`). Полная таблица — `./api-contract.md` §5.2.
- `kind: "text"` — нарративный текстовый блок ассистента (поля: `text`).
  Используется в составных ответах после tool-цикла. Полная таблица — §5.3.

Steam-обогащение (`gameId`, `storeUrl`, `imageUrl`) НЕ входит в контракт; вернётся
отдельным тикетом расширением этого DTO.

Полный пример (reasoning + одна игра):

```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
        "kind": "reasoning",
        "text": "Пользователь хочет лайтовую игру без шутеров. Учитывая историю чата, подобрал расслабляющие гонки и медитативные симуляторы."
      },
      {
        "kind": "game",
        "title": "Forza Horizon 5",
        "genre": "Racing, Open World",
        "description": "A vibrant open-world racing game set in Mexico, featuring a huge variety of cars and events.",
        "whyRecommended": "Perfect for short 30-minute sessions. Non-shooter, visually stunning, relaxing yet exciting.",
        "platforms": ["PC", "Xbox Series X/S"],
        "rating": 9.2,
        "releaseYear": "2021",
        "tags": ["Open-world", "Casual", "Short sessions"],
        "matchScore": 0.93
      }
    ]
  }
}
```

Минимальный пример (только обязательные поля карточки, без reasoning):

```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
        "kind": "game",
        "title": "Forza Horizon 5",
        "genre": "Racing, Open World",
        "description": "Open-world racing in Mexico.",
        "whyRecommended": "Short, non-shooter, relaxing.",
        "platforms": ["PC", "Xbox Series X/S"]
      }
    ]
  }
}
```

Составной пример с `kind: "text"` (типичный финальный ответ агента после tool-цикла —
сначала нарратив, потом карточки, опционально reasoning в конце):

```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
        "kind": "text",
        "text": "Нашёл два варианта в твоей библиотеке Steam, которые подходят под лайтовое настроение."
      },
      {
        "kind": "game",
        "title": "Stardew Valley",
        "genre": "Simulation, RPG",
        "description": "Спокойная фермерская симуляция.",
        "whyRecommended": "Короткие сессии, нет стресса.",
        "platforms": ["PC"]
      },
      {
        "kind": "reasoning",
        "text": "Опирался на профиль (180 часов в Disco Elysium, паттерн короткие вечерние сессии)."
      }
    ]
  }
}
```

## Status

`meta.type = "status"` — прогресс-индикатор.

`payload.state` is a string. Minimal allowed states:
- `thinking`
- `searching`
- `analyzing`

```json
{"schemaVersion":1,"type":"status","payload":{"state":"thinking"}}
```

## Error

`meta.type = "error"` — ошибка, видимая пользователю.

`payload`:
- `code` — machine-readable code
- `message` — debug/log message
- `retryable` — `true` if user can retry

```json
{
  "schemaVersion": 1,
  "type": "error",
  "payload": {
    "code": "AI_TIMEOUT",
    "message": "Upstream timeout",
    "retryable": true
  }
}
```

## Tool call (assistant initiates a tool)

`meta.type = "tool_call"`, `role = ASSISTANT`. Машинно-читаемый шаг агента,
парный `tool_result` приходит сообщением с ролью `TOOL` и тем же `toolCallId`.

Контракт зафиксирован, pipeline сейчас не генерит — включится с LangChain-релизом.

```json
{
  "messageId": "...",
  "role": "ASSISTANT",
  "content": "",
  "meta": {
    "schemaVersion": 1,
    "type": "tool_call",
    "payload": {
      "toolName": "steam_search",
      "args": { "query": "hades", "limit": 10 },
      "toolCallId": "call_a84a"
    }
  },
  "createdAt": "2026-04-26T18:53:00Z"
}
```

## Tool result (tool answers the call)

`meta.type = "tool_result"`, `role = TOOL`. `toolCallId` связывает результат с
конкретным `tool_call`. При ошибке инструмента — `result` отсутствует, `error`
содержит человекочитаемое сообщение.

Успешный ответ:

```json
{
  "messageId": "...",
  "role": "TOOL",
  "content": "",
  "meta": {
    "schemaVersion": 1,
    "type": "tool_result",
    "payload": {
      "toolName": "steam_search",
      "toolCallId": "call_a84a",
      "result": {
        "items": [{"appId": 1145360, "title": "Hades"}]
      }
    }
  },
  "createdAt": "2026-04-26T18:53:01Z"
}
```

Ошибка инструмента:

```json
{
  "schemaVersion": 1,
  "type": "tool_result",
  "payload": {
    "toolName": "steam_search",
    "toolCallId": "call_a84a",
    "error": "upstream timeout after 5s"
  }
}
```

## Reply (text-only)

```json
{
  "content": "Hello!",
  "meta": {
    "schemaVersion": 1,
    "type": "reply",
    "payload": {
      "text": "Hello!",
      "clientRequestId": "8a9d3a6a-9f3a-4c6c-9f0a-2e6f8d9a4f1b",
      "tags": ["RPG", "Story"],
      "extra": { "debug": true }
    }
  }
}
```

Пояснение опциональных полей `payload`:
- `clientRequestId` — id запроса клиента (дедупликация/трейс).
- `tags` — выбранные пользователем теги.
- `extra` — клиентские метаданные.

## Frontend Rendering Rules (Short)

- `meta.type == "cards"` → проходим `payload.items[]`, рендерим элементы по `kind`:
  - `reasoning` — блок-объяснение «Why these games»;
  - `game` — карточка игры;
  - `text` — нарративный текст ассистента (как обычный chat-bubble);
  - незнакомый `kind` — игнорируем.
- `meta.type == "status"` → индикатор по `payload.state`.
- `meta.type == "error"` → ошибка с retry, если `retryable=true`.
- `meta.type == "reply"` → рендерим `content` как обычный текст.
- `meta.type == "tool_call"` → можно отрисовать тонкий чип «Calling <toolName>…»,
  можно скрыть из видимой ленты — деталь UX, контракт нейтрален.
- `meta.type == "tool_result"` → по умолчанию **скрываем** из ленты (это
  машинный шаг агента, нужный для контекста, не для пользователя). Можно
  показать в дев-режиме как сворачиваемый debug-блок.
- Незнакомый `meta.type` → можно показать заглушку «обновите клиент».

## Limits

- recommended `meta` size limit: `<= 256KB`.
- recommended `content` size limit: `<= 8k` chars.
