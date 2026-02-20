package ru.perevalov.gamerecommenderai.message;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardsPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageErrorPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageMetaDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageMixedPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReplyPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageStatusPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageTraceDto;

/**
 * Фабрика канонического meta-контракта. Создает envelope и типовые payload-структуры через DTO.
 */
@Component
@RequiredArgsConstructor
public class MessageMetaFactory {
    private final ObjectMapper objectMapper;

    /**
     * Создает стандартную meta-оболочку schemaVersion/type/payload без trace.
     */
    public ObjectNode envelope(MessageMetaType type, Object payload) {
        return envelope(type, payload, null);
    }

    /**
     * Создает стандартную meta-оболочку schemaVersion/type/payload с опциональным trace.
     * Payload никогда не null: null заменяется на пустой объект.
     */
    public ObjectNode envelope(MessageMetaType type, Object payload, MessageTraceDto trace) {
        Object safePayload = payload != null ? payload : objectMapper.createObjectNode();
        MessageMetaDto<Object> meta = new MessageMetaDto<>(
                MessageMetaFields.SCHEMA_VERSION,
                type.wireName(),
                safePayload,
                trace
        );
        return objectMapper.valueToTree(meta);
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
    public ObjectNode status(String state, MessageTraceDto trace) {
        MessageStatusPayloadDto payload = new MessageStatusPayloadDto(state != null ? state : "");
        return envelope(MessageMetaType.STATUS, payload, trace);
    }

    /**
     * Шорткат для CARDS meta: payload = { "items": [...] } без trace.
     */
    public ObjectNode cards(List<MessageCardDto> items) {
        return cards(items, null);
    }

    /**
     * Шорткат для CARDS meta: payload = { "items": [...] } с опциональным trace.
     */
    public ObjectNode cards(List<MessageCardDto> items, MessageTraceDto trace) {
        List<MessageCardDto> safeItems = items != null ? items : Collections.emptyList();
        MessageCardsPayloadDto payload = new MessageCardsPayloadDto(safeItems);
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
    public ObjectNode error(String code, String message, boolean retryable, MessageTraceDto trace) {
        MessageErrorPayloadDto payload = new MessageErrorPayloadDto(
                code != null ? code : "",
                message != null ? message : "",
                retryable
        );
        return envelope(MessageMetaType.ERROR, payload, trace);
    }

    /**
     * Шорткат для MIXED meta: payload = { text, items, extra? } без trace.
     */
    public ObjectNode mixed(String text, List<MessageCardDto> items, JsonNode extra) {
        return mixed(text, items, extra, null);
    }

    /**
     * Шорткат для MIXED meta: payload = { text, items, extra? } с опциональным trace.
     */
    public ObjectNode mixed(String text, List<MessageCardDto> items, JsonNode extra, MessageTraceDto trace) {
        List<MessageCardDto> safeItems = items != null ? items : Collections.emptyList();
        MessageMixedPayloadDto payload = new MessageMixedPayloadDto(
                text != null ? text : "",
                safeItems,
                extra
        );
        return envelope(MessageMetaType.MIXED, payload, trace);
    }

    /**
     * Шорткат для REPLY meta: payload = { text } без trace.
     */
    public ObjectNode reply(String text) {
        return reply(text, null);
    }

    /**
     * Шорткат для REPLY meta: payload = { text } с опциональным trace.
     */
    public ObjectNode reply(String text, MessageTraceDto trace) {
        MessageReplyPayloadDto payload = new MessageReplyPayloadDto(text != null ? text : "");
        return envelope(MessageMetaType.REPLY, payload, trace);
    }

    /**
     * Создает DTO trace-объекта (requestId/runId).
     */
    public MessageTraceDto trace(String requestId, String runId) {
        return new MessageTraceDto(requestId, runId);
    }
}
