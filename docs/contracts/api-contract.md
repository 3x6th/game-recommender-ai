# API Contract (REST + Chat Meta)

This document defines the canonical REST API contract between frontend and backend
after introducing the chat meta-envelope. It complements:
- `docs/contracts/chat-message-meta.examples.md` (meta envelope + examples)
- `docs/contracts/java-python-contract.md` (Java <-> Python mapping)

## 0) Reality Check (Current Code)

- Current endpoint: `POST /api/v1/games/proceed`
- Current response DTO (`GameRecommendationResponse`) does not include `chatId` or `messages[]`
  and does not use the meta-envelope
- Read API for history does not exist yet
- `ChatMessage` entity already has fields: `chatId`, `role`, `content`, `meta (JsonNode)`, `clientRequestId`
- Springdoc (WebFlux) auto-generates OpenAPI, but `meta.type` and examples must be added manually

This task is primarily about documentation + contract. Code will be aligned later.

---

## 1) POST `/api/v1/games/proceed` Response Contract

### Response Shape

The response MUST contain:
- `chatId` (string/UUID)
- `messages[]` array for immediate FE rendering and storage

Each `message` MUST contain:
- `id`
- `role` (`USER` / `ASSISTANT` / optional `SYSTEM`)
- `content` (string, always present, even if cards/status exist)
- `meta` (meta-envelope `{ schemaVersion, type, payload }`)
- `createdAt` (ISO-8601)

### Meta Type Case (PR #70 alignment required)

Current examples use lowercase `type` values (`reply`, `cards`, `mixed`, `status`, `error`).
PR #70 JSON Schema expects `UPPER_SNAKE_CASE` (`^[A-Z_]+$`, e.g. `CHAT_MESSAGE`).
This MUST be согласовано before merging both PRs. Until then, treat the case as
an explicit compatibility decision, not an implicit assumption.

### Example (assistant returns text + cards -> `type = mixed`)

```json
{
  "chatId": "c2c0c8b2-6d1c-4f83-9a2a-1a7f6c7d9c10",
  "messages": [
    {
      "id": "2d1f2b2e-0b6f-46c1-8c2a-6a0b2b2eaa01",
      "role": "USER",
      "content": "Хочу что-то на 30 минут в день, без шутеров",
      "meta": {
        "schemaVersion": 1,
        "type": "reply",
        "payload": {}
      },
      "createdAt": "2026-02-28T16:10:00Z"
    },
    {
      "id": "2d1f2b2e-0b6f-46c1-8c2a-6a0b2b2eaa02",
      "role": "ASSISTANT",
      "content": "Ок! Под твой режим подойдут короткие сессии и кооператив. Вот варианты:",
      "meta": {
        "schemaVersion": 1,
        "type": "mixed",
        "payload": {
          "items": [
            {
              "title": "Overcooked! 2",
              "steamAppId": 728880,
              "whyRecommended": "Короткие сессии 15–30 минут, отличный кооп, не шутер.",
              "tags": ["Co-op", "Short sessions"],
              "matchScore": 0.95,
              "steamUrl": "https://store.steampowered.com/app/728880/Overcooked_2/"
            }
          ]
        }
      },
      "createdAt": "2026-02-28T16:10:02Z"
    }
  ]
}
```

---

## 2) Read API: `GET /chats/{chatId}/messages`

### Query Params

- `limit` (1..100, default 20)
- `before` (opaque cursor for pagination backward in history)

### Response Shape

- `chatId`
- `messages[]`
- `nextBefore` (cursor for next request)
- `hasMore` (optional boolean)

### Cursor Pagination Rules

- Sort order: `createdAt DESC, id DESC`
- Cursor encodes `{ createdAt, id }`
- Cursor MUST be opaque to FE (recommend `base64(JSON)`), FE passes it back without parsing
- Backend does NOT guarantee cursor format stability between versions

### Example

```json
{
  "chatId": "c2c0c8b2-6d1c-4f83-9a2a-1a7f6c7d9c10",
  "messages": [
    {
      "id": "2d1f2b2e-0b6f-46c1-8c2a-6a0b2b2eaa02",
      "role": "ASSISTANT",
      "content": "Ок! Под твой режим подойдут короткие сессии и кооператив. Вот варианты:",
      "meta": {
        "schemaVersion": 1,
        "type": "mixed",
        "payload": {
          "items": []
        }
      },
      "createdAt": "2026-02-28T16:10:02Z"
    }
  ],
  "nextBefore": "eyJjcmVhdGVkQXQiOiIyMDI2LTAyLTI4VDE2OjEwOjAyWiIsImlkIjoiMmQxZjJiMmUtMGI2Zi00NmMxLThjMmEtNmEwYjJiMmVhYTAyIn0=",
  "hasMore": true
}
```

---

## 3) Ownership Check Behavior

If a user tries to read чужой `chatId`, return:
- `404 Not Found`

This hides existence of чужих чатов.

### Error Body

Use standard API error response (no `requestId` in body):

```json
{
  "status": 404,
  "path": "/chats/{chatId}/messages",
  "errorCode": "chat not found",
  "message": "Chat not found. chatId=<id>",
  "timestamp": "2026-02-28T16:10:02"
}
```

Notes:
- `errorCode` is derived from `ErrorType` (e.g., `CHAT_NOT_FOUND` -> `chat not found`)
- `timestamp` format follows backend default serialization for `LocalDateTime`

---

## 4) `message.meta.type` Definitions + Examples

### 4.1 reply
Text-only (content is the main UI).

```json
{
  "schemaVersion": 1,
  "type": "reply",
  "payload": {}
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
        "steamAppId": 413150,
        "whyRecommended": "Спокойный игровой цикл и короткие сессии.",
        "tags": ["Chill", "Indie"],
        "matchScore": 0.9,
        "steamUrl": "https://store.steampowered.com/app/413150/Stardew_Valley/"
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
    "code": "SEARCHING_GAMES",
    "message": "Подбираю варианты под твои теги..."
  }
}
```

### 4.5 error
Error message visible to user.

```json
{
  "schemaVersion": 1,
  "type": "error",
  "payload": {
    "code": "AI_UNAVAILABLE",
    "message": "AI сервис временно недоступен. Попробуй еще раз."
  }
}
```

---

## 5) FE Fallback Rule (Mandatory)

If `meta.type` is unknown or unsupported:
- FE MUST render `content` as plain text
- FE MAY log unsupported types in dev mode

This ensures forward compatibility when BE adds new types.

---

## 6) Mapping Rules (gRPC -> HTTP meta.type)

When mapping from Python `RecommendationResponse` to HTTP `message.meta.type`:

- if gRPC has both text recommendation and game cards -> `mixed`
- if only text -> `reply`
- if only cards -> `cards`
- if gRPC returns error/fallback -> `error`
- if backend sends intermediate progress (future SSE) -> `status`

This must be documented and consistent across services.

---

## 7) OpenAPI/Springdoc Notes (Documentation Scope)

To make Springdoc reflect this contract, update DTO models and schema annotations:
- `ProceedResponse { chatId, messages[] }`
- `ChatMessageDto { messageId, role, content, meta, createdAt }`
- `MetaEnvelope { schemaVersion, type, payload }`
- `meta.type` -> enum with explicit allowable values
- Add OpenAPI examples for each type (`reply`, `cards`, `mixed`, `status`, `error`)
