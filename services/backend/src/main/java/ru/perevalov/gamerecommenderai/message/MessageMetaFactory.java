package ru.perevalov.gamerecommenderai.message;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageMetaFactory {
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    /**
     * Создает стандартную meta-оболочку schemaVersion/type/payload без trace.
     */
    public ObjectNode envelope(MessageMetaType type, JsonNode payload) {
        return envelope(type, payload, null);
    }

    /**
     * Создает стандартную meta-оболочку schemaVersion/type/payload с опциональным trace.
     * Payload никогда не null: null заменяется на пустой объект.
     */
    public ObjectNode envelope(MessageMetaType type, JsonNode payload, ObjectNode trace) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", SCHEMA_VERSION);
        meta.put("type", type.wireName());
        meta.set("payload", payload != null ? payload : objectMapper.createObjectNode());

        if (trace != null) {
            meta.set("trace", trace);
        }

        return meta;
    }

    /**
     * Шорткат для STATUS meta: payload = { "state": "<state>" } без trace.
     */
    public ObjectNode status(String state) {
        return status(state, null);
    }

    /**
     * Шорткат для STATUS meta: payload = { "state": "<state>" } с опциональным trace.
     */
    public ObjectNode status(String state, ObjectNode trace) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("state", state != null ? state : "");
        return envelope(MessageMetaType.STATUS, payload, trace);
    }

    /**
     * Шорткат для CARDS meta: payload = { "items": [...] } без trace.
     */
    public ObjectNode cards(JsonNode items) {
        return cards(items, null);
    }

    /**
     * Шорткат для CARDS meta: payload = { "items": [...] } с опциональным trace.
     */
    public ObjectNode cards(JsonNode items, ObjectNode trace) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("items", items != null ? items : objectMapper.createArrayNode());
        return envelope(MessageMetaType.CARDS, payload, trace);
    }

    /**
     * Шорткат для ERROR meta: payload = { code, message, retryable } без trace.
     */
    public ObjectNode error(String code, String message, boolean retryable) {
        return error(code, message, retryable, null);
    }

    /**
     * Шорткат для ERROR meta: payload = { code, message, retryable } с опциональным trace.
     */
    public ObjectNode error(String code, String message, boolean retryable, ObjectNode trace) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code != null ? code : "");
        payload.put("message", message != null ? message : "");
        payload.put("retryable", retryable);
        return envelope(MessageMetaType.ERROR, payload, trace);
    }

    /**
     * Шорткат для MIXED meta: payload = { text, items, extra? } без trace.
     */
    public ObjectNode mixed(String text, JsonNode items, ObjectNode extra) {
        return mixed(text, items, extra, null);
    }

    /**
     * Шорткат для MIXED meta: payload = { text, items, extra? } с опциональным trace.
     */
    public ObjectNode mixed(String text, JsonNode items, ObjectNode extra, ObjectNode trace) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("text", text != null ? text : "");
        payload.set("items", items != null ? items : objectMapper.createArrayNode());

        if (extra != null) {
            payload.set("extra", extra);
        }

        return envelope(MessageMetaType.MIXED, payload, trace);
    }

    /**
     * Создает trace-объект с опциональными requestId и runId.
     */
    public ObjectNode trace(String requestId, String runId) {
        ObjectNode trace = objectMapper.createObjectNode();
        if (requestId != null) {
            trace.put("requestId", requestId);
        }
        if (runId != null) {
            trace.put("runId", runId);
        }
        return trace;
    }
}
