# API Contract (REST + Chat Meta)

This document defines the canonical REST API contract between frontend and backend
after introducing the chat meta-envelope. It complements:
- `./chat-message-meta.examples.md` (meta envelope + examples)
- `./java-python-contract.md` (Java <-> Python mapping)

## 0) Reality Check (Current Code)

State as of [PCAI-141](https://jira.ozero.dev/browse/PCAI-141):

- Endpoint: `POST /api/v1/games/proceed` exists, but currently returns the legacy flat
  DTO `GameRecommendationResponse` (`recommendation, reasoning, success, recommendations,
  chatId, assistantMessageId, errorMessage`) — **without** `meta`.
- Meta-envelope IS already built and persisted to DB (`PersistAssistantStep` →
  `MessageMetaType.REPLY` / `MIXED`), so `GET /api/v1/chats/{chatId}/messages` already
  returns `meta` correctly.
- `ChatMessage` entity has fields: `chatId`, `role`, `content`, `meta (JsonNode)`,
  `clientRequestId`.
- Springdoc (WebFlux) auto-generates OpenAPI, but `meta.type` and examples must be
  added manually.

PCAI-141 covers aligning `/proceed` to the same envelope shape as `GET /chats/{id}/messages`.

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

`meta.type` is **lowercase** (`reply`, `cards`, `mixed`, `status`, `error`, `tool_call`,
`tool_result`). This matches `MessageMetaType.wireName()` in code and is the canonical
form across both `/proceed` and `GET /chats/{id}/messages`.

### Example: assistant returns text + cards (`type = mixed`)

```json
{
  "chatId": "c341060c-ad26-40d6-999e-36d4d737ebec",
  "messages": [
    {
      "messageId": "57563528-34a7-4a38-acaa-62a9e31389e3",
      "role": "ASSISTANT",
      "content": "Под твой запрос подойдут расслабляющие гонки и медитативные симуляторы. Вот варианты:",
      "meta": {
        "schemaVersion": 1,
        "type": "mixed",
        "payload": {
          "text": "Под твой запрос подойдут расслабляющие гонки и медитативные симуляторы. Вот варианты:",
          "items": [
            {
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
    "content": "Под твой запрос подойдут расслабляющие гонки и медитативные симуляторы. Вот варианты:",
    "meta": {
      "schemaVersion": 1,
      "type": "mixed",
      "payload": {
        "text": "...",
        "items": [
          { "title": "Forza Horizon 5", "genre": "Racing, Open World", "rating": 9.2 }
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
Cards-only (content optional, cards are primary).

```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
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

### 4.3 mixed
Text + cards in a single message.

```json
{
  "schemaVersion": 1,
  "type": "mixed",
  "payload": {
    "text": "Вот что подходит:",
    "items": []
  }
}
```

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

---

## 5) Card Fields (Canonical)

The card object inside `meta.payload.items[]` has the following shape. There is exactly
**one** card DTO (`MessageCardDto`) and it carries everything the LLM produces. No
Steam-side enrichment / store linking is part of this contract — if/when we want store
URLs or cover images, that's a separate change to this DTO, not a separate object.

| Field            | Type            | Required | Notes                                              |
|------------------|-----------------|----------|----------------------------------------------------|
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

## 6) FE Fallback Rule (Mandatory)

If `meta.type` is unknown or unsupported:
- FE MUST render `content` as plain text.
- FE MAY log unsupported types in dev mode.

This ensures forward compatibility when BE adds new types.

---

## 7) Mapping Rules (gRPC → HTTP `meta.type`)

When mapping from Python `RecommendationResponse` to HTTP `message.meta.type`:

- gRPC has both text and game cards → `mixed`
- only text → `reply`
- only cards → `cards`
- gRPC returns error/fallback → `error`
- backend emits intermediate progress (future SSE) → `status`

This must be documented and consistent across services.

---

## 8) OpenAPI / Springdoc (Documentation Scope)

To make Springdoc reflect this contract, update DTO models and schema annotations:
- `ProceedResponse { chatId, messages[], userMessage? }`
- `ChatMessageDto { messageId, role, content, meta, createdAt }`
- `MetaEnvelope { schemaVersion, type, payload, trace? }`
- `MessageCardDto { title, genre, description, whyRecommended, platforms, rating?, releaseYear?, tags?, matchScore? }`
- `meta.type` → enum with explicit allowable values (lowercase wire form)
- Add OpenAPI examples for each type (`reply`, `cards`, `mixed`, `status`, `error`)
