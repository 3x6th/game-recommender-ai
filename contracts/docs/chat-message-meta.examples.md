# Chat Message Meta Examples

This document fixes the canonical shape of `chat_messages.meta` and provides examples.
For REST API contract and pagination rules, see `./api-contract.md`.

`meta.type` is **lowercase** (`reply`, `cards`, `mixed`, `status`, `error`,
`tool_call`, `tool_result`) — matches `MessageMetaType.wireName()` in code.

## Canonical Envelope

Every meta object MUST follow the same envelope:

```json
{
  "schemaVersion": 1,
  "type": "reply|cards|status|error|mixed|...",
  "payload": {},
  "trace": {
    "requestId": "optional",
    "runId": "optional"
  }
}
```

Rules:
- `content` is the bubble text shown in chat UI (always present, used as FE fallback render)
- `schemaVersion` is an integer and `>= 1`
- `type` is a non-empty string
- `payload` is required (can be `{}`); today usually object, but may be array/string later
- `trace` is optional; if present it must be an object with optional string fields `requestId` and `runId`
- `meta` is **required for every role**, including USER. USER messages are stored and
  returned with `meta.type = "reply"` and `payload.text = content`. This keeps chat
  history symmetric (one DTO, one (de)serializer for both sides of the conversation).

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

We use `meta.type = "cards"` for recommendation cards.

Cards live in `meta.payload.items`. Card field reference is in
`./api-contract.md` (section 5). Steam-specific fields (`gameId`, `storeUrl`,
`imageUrl`) are deferred to a separate ticket (*Steam mapping & enrichment*) and are
**not** part of the current card contract.

Required card fields (fixed contract for FE): `title`, `genre`, `description`,
`whyRecommended`, `platforms[]`. Optional: `rating`, `releaseYear`, `tags[]`,
`matchScore`.

Example:
```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
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
  }
}
```

Minimal example (only required fields):
```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
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

## Status

We use `meta.type = "status"` for progress updates.

`payload.state` is a string. Minimal allowed states:
- `thinking`
- `searching`
- `analyzing`

Example:
```json
{
  "schemaVersion": 1,
  "type": "status",
  "payload": {
    "state": "thinking"
  }
}
```

Minimal example:
```json
{"schemaVersion":1,"type":"status","payload":{"state":"thinking"}}
```

## Error

We use `meta.type = "error"` for failures.

`payload` fields:
- `code` — machine-readable code
- `message` — debug/log message
- `retryable` — `true` if user can retry

Example:
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

Minimal example:
```json
{"schemaVersion":1,"type":"error","payload":{"code":"AI_TIMEOUT","message":"Upstream timeout","retryable":true}}
```

## Frontend Rendering Rules (Short)

- if `meta.type == "cards"` → render cards from `payload.items`
- if `meta.type == "status"` → show indicator using `payload.state`
- if `meta.type == "error"` → show error + retry when `retryable=true`
- else → fallback to `content`
- FE MUST ignore unknown fields, and unknown types must not break rendering

## Limits

- recommended `meta` size limit: `<= 256KB`
- recommended `content` size limit: `<= 8k` chars

## Optional: Reply / Mixed (for completeness)

Reply example (with optional fields, пояснение ниже):
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

Пояснение:
- `clientRequestId` — опциональный id запроса клиента (дедупликация/трейс)
- `tags` — опциональные выбранные пользователем теги
- `extra` — опциональные метаданные для клиента

Mixed example (text + cards):
```json
{
  "schemaVersion": 1,
  "type": "mixed",
  "payload": {
    "text": "Вот что подходит",
    "items": [
      {
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
