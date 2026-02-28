# Chat Message Meta Examples

This document fixes the canonical shape of `chat_messages.meta` and provides examples.
For REST API contract and pagination rules, see `docs/contracts/api-contract.md`.

Note: PR #70 JSON Schema expects `meta.type` in `UPPER_SNAKE_CASE`. This document
currently uses lowercase (`reply`, `cards`, `mixed`, `status`, `error`).
This MUST be aligned before merging both PRs.

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
- `content` is the bubble text shown in chat UI (can be null)
- `schemaVersion` is an integer and `>= 1`
- `type` is a non-empty string
- `payload` is required (can be `{}`); today usually object, but may be array/string later
- `trace` is optional; if present it must be an object with optional string fields `requestId` and `runId`

## Cards (Canonical Form)

We use `meta.type = "cards"` for recommendation cards.

Cards live in `meta.payload.items`.

Minimal card fields (fixed contract for FE):
- `gameId` — unique id. For Steam: `steam:<appId>`
- `title` — display name
- `score` — number `0..1` (float), optional. If upstream uses `0..100`, normalize on BE before storing/returning
- `reason` — 1–2 sentences, optional
- `tags` — array of strings, optional
- `storeUrl` / `imageUrl` — optional

Example:
```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": {
    "items": [
      {
        "gameId": "steam:123",
        "title": "Disco-like RPG",
        "score": 0.92,
        "reason": "Сильный нарратив и выборы в диалогах",
        "tags": ["RPG", "Story"]
      }
    ]
  }
}
```

Minimal example (must be supported by FE):
```json
{
  "schemaVersion": 1,
  "type": "cards",
  "payload": { "items": [ { "gameId": "steam:123", "title": "Game" } ] }
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
      { "gameId": "steam:123", "title": "Game" }
    ]
  }
}
```
