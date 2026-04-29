# API Contract (REST + Chat Meta)

This document defines the canonical REST API contract between frontend and backend
after introducing the chat meta-envelope. It complements:
- `./chat-message-meta.examples.md` (meta envelope + examples)
- `./java-python-contract.md` (Java <-> Python mapping)

## 0) Reality Check (Current Code)

State as of [PCAI-141](https://jira.ozero.dev/browse/PCAI-141) follow-up:

- Endpoint `POST /api/v1/games/proceed` отдаёт `ProceedResponse { chatId, messages[] }`.
  Каждый `message` — стандартный `ChatMessageDto` (тот же, что `GET /chats/{id}/messages`).
- `meta.type = mixed` упразднён. Ассистентский ответ всегда сериализуется как
  `cards` с полиморфным `payload.items[]` (см. §4.2 / §5). Reasoning живёт
  внутри `items[]` отдельным элементом `kind: "reasoning"`.
- `meta.payload.text`, `meta.payload.reasoning`, `meta.payload.extra` удалены полностью.
  Никакого fallback на легаси FE нет — стенд `MVP-Major-1.5` остаётся на старом
  контракте, релиз 2 идёт на новом.
- `ChatMessage` entity: `chatId`, `role`, `content`, `meta (jsonb)`, `clientRequestId`.
  Для `cards`-сообщений `content` пустой — всё рисуется из `items[]`.
- Springdoc (WebFlux) сам генерит OpenAPI; `meta.type` и примеры — через `@Schema`.

---

## 1) POST `/api/v1/games/proceed` Response Contract

### Response Shape

The response MUST contain:
- `chatId` (string/UUID)
- `messages[]` — assistant messages produced by this turn (typically 1, may be more in
  the future for status streams). The user's own message is **not** echoed: FE already
  has it; if FE needs the server-assigned id/createdAt for optimistic state, they are
  returned separately as `userMessage` (optional).
- `userMessage` (optional) — only `{ messageId, createdAt }` for the just-stored user
  message. Use only if FE needs to reconcile optimistic UI; otherwise omit.

### Single Message Shape (used everywhere)

The **same** `ChatMessageDto` shape is used for both USER and ASSISTANT messages, in
both `/proceed` response and `GET /chats/{id}/messages` response. There is exactly one
DTO and one (de)serializer for chat messages — FE never has to branch by role to parse.

Each `message` MUST contain:
- `messageId` (UUID)
- `role` (`USER` / `ASSISTANT` / `SYSTEM`)
- `content` (string, always present — used as the FE fallback render)
- `meta` (meta-envelope `{ schemaVersion, type, payload, trace? }`) — **required for
  every role**, including USER (see §4.1)
- `createdAt` (ISO-8601, UTC)

### `meta.type` Casing

`meta.type` is **lowercase** (`reply`, `cards`, `status`, `error`, `tool_call`,
`tool_result`). This matches `MessageMetaType.wireName()` in code and is the canonical
form across both `/proceed` and `GET /chats/{id}/messages`.

### Example: assistant returns reasoning + cards (`type = cards`)

```json
{
  "chatId": "c341060c-ad26-40d6-999e-36d4d737ebec",
  "messages": [
    {
      "messageId": "57563528-34a7-4a38-acaa-62a9e31389e3",
      "role": "ASSISTANT",
      "content": "",
      "meta": {
        "schemaVersion": 1,
        "type": "cards",
        "payload": {
          "items": [
            {
              "kind": "reasoning",
              "text": "Пользователь хочет лайтовую игру без шутеров. Учитывая историю чата, я подобрал расслабляющие гонки."
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
        },
        "trace": {
          "requestId": "a84a7438-f26c-43f2-b31d-733ac5366f12",
          "runId": "..."
        }
      },
      "createdAt": "2026-04-26T18:53:01Z"
    }
  ]
}
```

`content` для `cards`-сообщений пустой — всё содержимое лежит в `payload.items[]`.
FE рендерит элементы по полю `kind`.

### Example: AI failure (`type = error`)

```json
{
  "chatId": "c341060c-ad26-40d6-999e-36d4d737ebec",
  "messages": [
    {
      "messageId": "<uuid>",
      "role": "ASSISTANT",
      "content": "AI сервис временно недоступен. Попробуй ещё раз.",
      "meta": {
        "schemaVersion": 1,
        "type": "error",
        "payload": {
          "code": "AI_UNAVAILABLE",
          "message": "AI сервис временно недоступен. Попробуй ещё раз.",
          "retryable": true
        }
      },
      "createdAt": "2026-04-26T18:53:01Z"
    }
  ]
}
```

Distinction:
- **Pipeline-level failures visible to user** (AI down, upstream timeout, no results) →
  HTTP 200 with a single `meta.type = error` message in `messages[]`.
- **Infra/contract failures** (5xx, validation errors, unauthorized) → standard
  `ApiError` body with HTTP 4xx/5xx, as handled by `GlobalExceptionHandler`.

---

## 2) Read API: `GET /api/v1/chats/{chatId}/messages`

### Query Params

- `limit` (1..100, default 20) — clamped silently with `X-Pagination-Limit-Adjusted`
  header on adjustment.
- `before` (timestamp) — return messages with `createdAt < before`.

### Response Shape

A flux of `ChatMessageDto` items in `createdAt DESC` order:

```json
[
  {
    "messageId": "2d1f2b2e-0b6f-46c1-8c2a-6a0b2b2eaa02",
    "role": "ASSISTANT",
    "content": "",
    "meta": {
      "schemaVersion": 1,
      "type": "cards",
      "payload": {
        "items": [
          { "kind": "reasoning", "text": "Пользователь хочет лайтовую игру. Подобрал расслабляющие гонки." },
          { "kind": "game", "title": "Forza Horizon 5", "genre": "Racing, Open World", "rating": 9.2 }
        ]
      }
    },
    "createdAt": "2026-02-28T16:10:02Z"
  }
]
```

The shape of `message.meta` is **identical** to the one returned by `/proceed`.

---

## 3) Ownership Check Behavior

If a user tries to read someone else's `chatId`, return `404 Not Found`. This hides the
existence of foreign chats.

### Error Body

Standard API error response:

```json
{
  "status": 404,
  "path": "/api/v1/chats/{chatId}/messages",
  "errorCode": "chat not found",
  "message": "Chat not found. chatId=<id>",
  "timestamp": "2026-02-28T16:10:02"
}
```

Notes:
- `errorCode` is derived from `ErrorType` (e.g., `CHAT_NOT_FOUND` → `chat not found`).
- `timestamp` follows backend default serialization for `LocalDateTime`.

`/proceed` does not accept `chatId` from the client today (it's resolved from request
context / `clientRequestId`), so 404-on-foreign-chat does not apply there.

---

## 4) `message.meta.type` Definitions + Examples

### 4.1 reply
Text-only (content is the main UI).

This is the canonical type for **USER messages** as well: when a user sends a chat
message, BE persists it with `meta.type = "reply"` and `payload.text = content`. This
keeps the chat history symmetric — both sides of the conversation use the same
envelope and the same `ChatMessageDto`.

```json
{
  "schemaVersion": 1,
  "type": "reply",
  "payload": { "text": "Hello!" }
}
```

### 4.2 cards
Любой ответ ассистента, содержащий карточки, нарративный текст или общий reasoning.
`content` всегда пустой — всё рендерится из `payload.items[]`.

`items[]` — полиморфный массив с дискриминатором `kind`:
- `kind: "reasoning"` — метакомментарий «почему именно этот набор» (LLM-уровень,
  отличается от `whyRecommended` внутри игровой карточки). Обычно идёт первым.
- `kind: "game"` — карточка игры (поля см. §5.2).
- `kind: "text"` — нарративный текстовый блок ассистента (см. §5.3).
  Используется в составных ответах после tool-цикла, когда агент пишет
  «вот что я нашёл по твоему запросу: ...» + карточки в одном сообщении.

FE рендерит сверху вниз. Незнакомые `kind` MUST игнорировать (forward-compat для
будущих типов — см. §6 «Reserved future kinds»).

```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
        "kind": "reasoning",
        "text": "Пользователь хочет лайтовую игру без шутеров. Подобрал расслабляющие симуляции."
      },
      {
        "kind": "game",
        "title": "Stardew Valley",
        "genre": "Simulation, RPG",
        "description": "Спокойная фермерская симуляция с лёгким сюжетом.",
        "whyRecommended": "Короткие сессии, нет стресса.",
        "platforms": ["PC", "PS5", "Xbox", "Switch"],
        "rating": 9.0,
        "releaseYear": "2016",
        "tags": ["Chill", "Indie"],
        "matchScore": 0.9
      }
    ]
  }
}
```

Hard-coded summaries вроде `"Received N recommendations"` MUST NOT попадать ни в
`content`, ни внутрь `kind: "reasoning"` — последнее зарезервировано под живой
вывод модели.

### 4.4 status
Intermediate/system status (useful for streaming and progress).

```json
{
  "schemaVersion": 1,
  "type": "status",
  "payload": {
    "state": "searching",
    "message": "Подбираю варианты под твои теги..."
  }
}
```

### 4.5 error
Error message visible to the user.

```json
{
  "schemaVersion": 1,
  "type": "error",
  "payload": {
    "code": "AI_UNAVAILABLE",
    "message": "AI сервис временно недоступен. Попробуй ещё раз.",
    "retryable": true
  }
}
```

### 4.6 tool_call

Сообщение ассистента, инициирующее вызов инструмента (Steam-поиск, similar games,
RAG и т.п.). Парный `tool_result` с тем же `toolCallId` приходит сообщением с
ролью `TOOL`.

`role = ASSISTANT`. `content` обычно пустой (это машинно-читаемый шаг агента).
FE по умолчанию **может отрисовать как тонкий статус-чип** «Calling <toolName>…»
или вовсе скрыть из видимой ленты — это деталь UX, контракт нейтрален.

Контракт зафиксирован для следующего релиза (LangChain/tools), сейчас pipeline
такие сообщения не генерит. Хранилище и валидатор уже совместимы.

```json
{
  "schemaVersion": 1,
  "type": "tool_call",
  "payload": {
    "toolName": "steam_search",
    "args": { "query": "hades", "limit": 10 },
    "toolCallId": "call_a84a"
  }
}
```

### 4.7 tool_result

Ответ инструмента на парный `tool_call`. `role = TOOL`. `toolCallId` связывает
результат с конкретным вызовом — нужен потому, что агент может запускать
несколько вызовов параллельно.

При ошибке инструмента — `result` отсутствует, `error` содержит человекочитаемое
сообщение.

```json
{
  "schemaVersion": 1,
  "type": "tool_result",
  "payload": {
    "toolName": "steam_search",
    "toolCallId": "call_a84a",
    "result": {
      "items": [{"appId": 1145360, "title": "Hades"}]
    }
  }
}
```

---

## 5) Polymorphic `items[]` (Canonical)

Все элементы внутри `meta.payload.items[]` обязаны иметь поле-дискриминатор `kind`.
На уровне Java это `sealed interface MessageItemDto` с тремя реализациями
(`MessageReasoningItemDto`, `MessageCardDto`, `MessageTextItemDto`). FE MUST ignore
unknown `kind` values.

### 5.1 `kind: "reasoning"`

| Field   | Type   | Required | Notes                                       |
|---------|--------|----------|---------------------------------------------|
| `kind`  | string | yes      | Constant `"reasoning"`                      |
| `text`  | string | yes      | Объяснение «почему именно этот набор игр»   |

Reasoning-карточка обычно идёт первой в `items[]` и рендерится как блок-объяснение
над списком игр. Может отсутствовать, если LLM ничего не вернул.

### 5.3 `kind: "text"` (`MessageTextItemDto`)

| Field   | Type   | Required | Notes                                                          |
|---------|--------|----------|----------------------------------------------------------------|
| `kind`  | string | yes      | Constant `"text"`                                              |
| `text`  | string | yes      | Нарративный ответ ассистента (отвечает на вопрос пользователя) |

Семантическая разница с `reasoning`: `reasoning` — метакомментарий про сам набор
карточек, `text` — это собственно ответ агента. В составном ответе после tool-цикла
типичный порядок: `[text, game, game, reasoning]` либо `[text]` без карточек.

### 5.2 `kind: "game"` (`MessageCardDto`)

Один канонический DTO для всех игровых карточек. Никакого Steam-обогащения
(`storeUrl`, `imageUrl`, `gameId`) тут нет — если когда-нибудь понадобится,
это отдельное расширение этого DTO, а не новая сущность.

| Field            | Type            | Required | Notes                                              |
|------------------|-----------------|----------|----------------------------------------------------|
| `kind`           | string          | yes      | Constant `"game"`                                  |
| `title`          | string          | yes      | Display name                                       |
| `genre`          | string          | yes      | Comma-separated, e.g. `"Racing, Open World"`       |
| `description`    | string          | yes      | What the game is                                   |
| `whyRecommended` | string          | yes      | Why this user gets this card                       |
| `platforms`      | array<string>   | yes      | e.g. `["PC", "Xbox Series X/S"]`                   |
| `rating`         | number          | no       | `0..10` (LLM-style scale)                          |
| `releaseYear`    | string          | no       | String, not number — LLM may emit `"TBD"`          |
| `tags`           | array<string>   | no       | Free-form, e.g. `["Co-op","Short sessions"]`       |
| `matchScore`     | number          | no       | `0..1`, how well the card matches the user request |

FE MUST ignore unknown fields. BE MUST omit `null`-valued optional fields
(`@JsonInclude(NON_NULL)`).

---

## 6) Forward Compatibility

- Незнакомые `meta.type` FE MUST gracefully скипать (можно показать заглушку
  «обновите клиент»). Никакого fallback-чтения старых полей (`payload.text`,
  `payload.reasoning`, `payload.extra`) не делается — этих полей в новом
  контракте нет.
- Незнакомые `items[].kind` FE MUST игнорировать. Это даёт нам возможность
  безопасно добавлять новые виды элементов без поломки уже задеплоенного клиента.

### 6.1 Reserved future kinds (roadmap)

Зарезервированы для следующих релизов. Контракт каждого фиксируется тем тикетом,
который вводит соответствующую LLM-логику. До этого момента BE не генерит эти
kind'ы, FE безопасно игнорирует если случайно встретит.

| `kind`                  | Назначение                                                  | Релиз            |
|-------------------------|-------------------------------------------------------------|------------------|
| `profile_review`        | Оценка профиля пользователя (топ-жанры, паттерны, инсайты)  | TBD              |
| `clarifying_question`   | Уточняющий вопрос ассистента, когда нужно больше контекста  | TBD              |
| `quick_replies`         | Быстрые ответы-кнопки для пользователя                      | TBD              |

При добавлении: новый DTO в `message/dto/`, строчка в `MessageItemDto.@JsonSubTypes`,
раздел в §5, FE `switch(kind)` ветка, пример в `chat-message-meta.examples.md`.

### 6.2 Reserved future meta types

| `meta.type`     | Назначение                                                      | Релиз                   |
|-----------------|-----------------------------------------------------------------|-------------------------|
| `tool_call`     | Машинный запуск инструмента ассистентом-агентом (см. §4.6)      | LangChain/tools релиз   |
| `tool_result`   | Ответ инструмента на парный `tool_call` (см. §4.7)              | LangChain/tools релиз   |

`MessageMetaType` enum их уже содержит, payload-DTO (`MessageToolCallPayloadDto`,
`MessageToolResultPayloadDto`) и validator-allowlist расширены — pipeline начнёт
их генерить, когда подключится LangChain-сторона.

---

## 7) Mapping Rules (gRPC → HTTP `meta.type`)

When mapping from Python `RecommendationResponse` to HTTP `message.meta.type`:

- gRPC возвращает reasoning и/или recommendations → `cards` с
  полиморфным `items[]` (reasoning-блок + игровые карточки).
- gRPC вернул только текст без карточек и без reasoning → `reply`
  (`payload.text`).
- gRPC вернул error/fallback → `error`.
- Backend emits intermediate progress (future SSE) → `status`.
- Внутренние шаги агента (LangChain tool-цикл) → `tool_call` от ассистента,
  `tool_result` от роли `TOOL`. Эти сообщения сохраняются в чат-истории
  для последующего использования агентом как контекст.

This must be documented and consistent across services.

---

## 8) OpenAPI / Springdoc (Documentation Scope)

To make Springdoc reflect this contract, update DTO models and schema annotations:
- `ProceedResponse { chatId, messages[], userMessage? }`
- `ChatMessageDto { messageId, role, content, meta, createdAt }`
- `MetaEnvelope { schemaVersion, type, payload, trace? }`
- `MessageItemDto` — polymorphic (Schema discriminator `kind`):
  - `MessageReasoningItemDto { kind: "reasoning", text }`
  - `MessageCardDto { kind: "game", title, genre, description, whyRecommended, platforms, rating?, releaseYear?, tags?, matchScore? }`
  - `MessageTextItemDto { kind: "text", text }`
- `meta.type` → enum with explicit allowable values (lowercase wire form):
  `reply`, `cards`, `status`, `error`, `tool_call`, `tool_result`
- Add OpenAPI examples for each type
