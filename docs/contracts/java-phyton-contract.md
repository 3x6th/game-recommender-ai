Java ↔ Python Contract (AI recommendations)

This document defines the cross-service contract between Java backend and Python AI service.

## Scope

- Java → Python request contract: `AiContextRequest`
- Python → Java response contract: `RecommendationResponse`
- Mapping from `RecommendationResponse` to canonical chat meta envelope (`schemaVersion/type/payload`)

---

## 1) Java → Python: `AiContextRequest`

### Fields

| Field | Type | Required | Description |
|---|---|---:|---|
| `message` | `string` | ✅ | User request text. |
| `tags` | `string[]` | ✅ | Selected tags/genres. Can be an empty array. |
| `profileSummary` | `string` | ✅ | Compact user profile summary (preferences, library, patterns). |
| `constraints` | `object` | ✅ | Generation constraints (limits, language, filters, etc.). |
| `requestId` | `string` | ✅ | Request identifier for tracing. |
| `chatId` | `string` | ❌ | Chat identifier (if chat context exists). |
| `agentId` | `string` | ❌ | AI agent/model/prompt-profile identifier. |

### JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://game-recommender-ai/contracts/ai-context-request.schema.json",
  "title": "AiContextRequest",
  "type": "object",
  "additionalProperties": false,
  "required": ["message", "tags", "profileSummary", "constraints", "requestId"],
  "properties": {
    "message": { "type": "string", "minLength": 1, "maxLength": 8000 },
    "tags": {
      "type": "array",
      "items": { "type": "string", "minLength": 1, "maxLength": 64 },
      "maxItems": 50,
      "uniqueItems": true
    },
    "profileSummary": { "type": "string", "minLength": 1, "maxLength": 16000 },
    "constraints": { "type": "object", "additionalProperties": true },
    "requestId": { "type": "string", "minLength": 1, "maxLength": 128 },
    "chatId": { "type": "string", "minLength": 1, "maxLength": 128 },
    "agentId": { "type": "string", "minLength": 1, "maxLength": 128 }
  }
}
```

---

## 2) Python → Java: `RecommendationResponse`

Python returns `RecommendationResponse` with top-level fields:

```json
{
  "type": "cards|status|error",
  "meta": {
    "schemaVersion": 1
  },
  "items": [],
  "debug": {},
  "trace": {
    "requestId": "...",
    "runId": "..."
  }
}
```

### Field definitions

| Field | Type | Required | Description |
|---|---|---:|---|
| `type` | `string` | ✅ | Response type (`cards`, `status`, `error`). |
| `meta` | `object` | ✅ | Response metadata (must include `schemaVersion`). |
| `items` | `array` | ✅ | Recommendation cards. For `status/error` typically empty array. |
| `debug` | `object` | ❌ | Optional diagnostics for logs/telemetry. |
| `trace` | `object` | ✅ | Trace object (`requestId` required, `runId` optional). |

Card item (`items[*]`) fields:

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `gameId` | `string` | ✅ | For example `steam:570`. |
| `title` | `string` | ✅ | Game title. |
| `score` | `number` | ❌ | Range `0..1` inclusive. |
| `reason` | `string` | ❌ | Short recommendation rationale. |
| `tags` | `string[]` | ❌ | Tag list. |
| `storeUrl` | `string` | ❌ | Game page URL. |
| `imageUrl` | `string` | ❌ | Cover image URL. |

### JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://game-recommender-ai/contracts/recommendation-response.schema.json",
  "title": "RecommendationResponse",
  "type": "object",
  "additionalProperties": false,
  "required": ["type", "meta", "items", "trace"],
  "properties": {
    "type": { "type": "string", "enum": ["cards", "status", "error"] },
    "meta": {
      "type": "object",
      "additionalProperties": true,
      "required": ["schemaVersion"],
      "properties": {
        "schemaVersion": { "type": "integer", "minimum": 1 }
      }
    },
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": true,
        "required": ["gameId", "title"],
        "properties": {
          "gameId": { "type": "string", "minLength": 1 },
          "title": { "type": "string", "minLength": 1 },
          "score": { "type": "number", "minimum": 0, "maximum": 1 },
          "reason": { "type": "string" },
          "tags": { "type": "array", "items": { "type": "string" } },
          "storeUrl": { "type": "string", "format": "uri" },
          "imageUrl": { "type": "string", "format": "uri" }
        }
      }
    },
    "debug": {
      "type": "object",
      "additionalProperties": true
    },
    "trace": {
      "type": "object",
      "additionalProperties": false,
      "required": ["requestId"],
      "properties": {
        "requestId": { "type": "string", "minLength": 1 },
        "runId": { "type": "string", "minLength": 1 }
      }
    }
  },
  "oneOf": [
    {
      "properties": {
        "type": { "const": "cards" }
      }
    },
    {
      "properties": {
        "type": { "const": "status" },
        "meta": {
          "required": ["schemaVersion", "state"]
        }
      }
    },
    {
      "properties": {
        "type": { "const": "error" },
        "meta": {
          "required": ["schemaVersion", "code", "message", "retryable"]
        }
      }
    }
  ]
}
```

---

## 3) Mapping to chat meta format (`schemaVersion/type/payload`)

`RecommendationResponse` is mapped by Java to canonical chat meta envelope:

```json
{
  "schemaVersion": 1,
  "type": "cards|status|error",
  "payload": {},
  "trace": {
    "requestId": "...",
    "runId": "..."
  }
}
```

### Mapping rules

- `chat.meta.schemaVersion = RecommendationResponse.meta.schemaVersion`
- `chat.meta.type = RecommendationResponse.type`
- `chat.meta.trace = RecommendationResponse.trace`
- For `type = "cards"`:
    - `chat.meta.payload = { "items": RecommendationResponse.items }`
- For `type = "status"`:
    - `chat.meta.payload = { "state": RecommendationResponse.meta.state, "message": RecommendationResponse.meta.message? }`
- For `type = "error"`:
    - `chat.meta.payload = { "code": RecommendationResponse.meta.code, "message": RecommendationResponse.meta.message, "retryable": RecommendationResponse.meta.retryable }`
- `RecommendationResponse.debug` is optional and can be stored in logs/telemetry; it is not required in `chat.meta`.

---

## 4) JSON examples (3)

### 4.1 Normal response with cards

```json
{
  "type": "cards",
  "meta": {
    "schemaVersion": 1,
    "provider": "deepseek",
    "model": "deepseek-chat"
  },
  "items": [
    {
      "gameId": "steam:570",
      "title": "Dota 2",
      "score": 0.94,
      "reason": "High replay value and strong competitive gameplay.",
      "tags": ["MOBA", "Competitive", "Multiplayer"],
      "storeUrl": "https://store.steampowered.com/app/570",
      "imageUrl": "https://cdn.akamai.steamstatic.com/steam/apps/570/header.jpg"
    },
    {
      "gameId": "steam:730",
      "title": "Counter-Strike 2",
      "score": 0.89,
      "reason": "Matches the request for fast team-based rounds.",
      "tags": ["FPS", "Competitive"]
    }
  ],
  "debug": {
    "latencyMs": 1480,
    "promptTokens": 1220,
    "completionTokens": 390
  },
  "trace": {
    "requestId": "req-2fd90a6f",
    "runId": "run-9aa83c"
  }
}
```

### 4.2 Error / timeout

```json
{
  "type": "error",
  "meta": {
    "schemaVersion": 1,
    "code": "AI_TIMEOUT",
    "message": "Upstream model timeout after 10s",
    "retryable": true
  },
  "items": [],
  "debug": {
    "upstream": "deepseek",
    "timeoutMs": 10000
  },
  "trace": {
    "requestId": "req-4b11f7c9",
    "runId": "run-f1dd84"
  }
}
```

### 4.3 Status response (no cards yet)

```json
{
  "type": "status",
  "meta": {
    "schemaVersion": 1,
    "state": "thinking",
    "message": "Analyzing library and genre preferences"
  },
  "items": [],
  "trace": {
    "requestId": "req-77f8dcf0"
  }
}
```