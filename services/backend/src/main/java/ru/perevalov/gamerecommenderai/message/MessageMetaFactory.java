package ru.perevalov.gamerecommenderai.message;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardsPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageErrorPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageMetaDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReplyPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageStatusPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageTextItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageToolCallPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageToolResultPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageTraceDto;

/**
 * Фабрика канонического meta-контракта. Создает envelope и типовые payload-структуры.
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
     * Шорткат для CARDS meta: payload = { items: [...] } без trace.
     *
     * <p>{@code items[]} — полиморфный список ({@link MessageItemDto}):
     * первый элемент обычно reasoning-блок, остальные — игровые карточки.
     */
    public ObjectNode cards(List<? extends MessageItemDto> items) {
        return cards(items, null);
    }

    /**
     * Шорткат для CARDS meta: payload = { items: [...] } с опциональным trace.
     */
    public ObjectNode cards(List<? extends MessageItemDto> items, MessageTraceDto trace) {
        List<MessageItemDto> safeItems = items != null
                ? List.copyOf(items)
                : Collections.emptyList();
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
     * Шорткат для REPLY meta: payload = { text } без trace.
     */
    public ObjectNode reply(String text) {
        return reply(text, null, null, null, null);
    }

    /**
     * Шорткат для REPLY meta: payload = { text } с опциональным trace.
     */
    public ObjectNode reply(String text, MessageTraceDto trace) {
        return reply(text, null, null, null, trace);
    }

    /**
     * Шорткат для REPLY meta: payload = { text, clientRequestId?, tags?, extra? } без trace.
     */
    public ObjectNode reply(String text, UUID clientRequestId, List<String> tags, JsonNode extra) {
        return reply(text, clientRequestId, tags, extra, null);
    }

    /**
     * Шорткат для REPLY meta: payload = { text, clientRequestId?, tags?, extra? } с опциональным trace.
     */
    public ObjectNode reply(
            String text,
            UUID clientRequestId,
            List<String> tags,
            JsonNode extra,
            MessageTraceDto trace
    ) {
        MessageReplyPayloadDto payload = new MessageReplyPayloadDto(
                text != null ? text : "",
                clientRequestId,
                tags,
                extra
        );
        return envelope(MessageMetaType.REPLY, payload, trace);
    }

    /**
     * Шорткат для TOOL_CALL meta: payload = { toolName, args, toolCallId } без trace.
     */
    public ObjectNode toolCall(String toolName, JsonNode args, String toolCallId) {
        return toolCall(toolName, args, toolCallId, null);
    }

    /**
     * Шорткат для TOOL_CALL meta: payload = { toolName, args, toolCallId } с опциональным trace.
     *
     * <p>Сериализуется как сообщение ассистента, инициирующее вызов инструмента.
     * Парный {@code tool_result} с тем же {@code toolCallId} приходит сообщением
     * с ролью {@code TOOL}.
     */
    public ObjectNode toolCall(String toolName, JsonNode args, String toolCallId, MessageTraceDto trace) {
        MessageToolCallPayloadDto payload = MessageToolCallPayloadDto.builder()
                .toolName(toolName != null ? toolName : "")
                .args(args)
                .toolCallId(toolCallId)
                .build();
        return envelope(MessageMetaType.TOOL_CALL, payload, trace);
    }

    /**
     * Шорткат для TOOL_RESULT meta: payload = { toolName, toolCallId, result } без trace.
     */
    public ObjectNode toolResult(String toolName, String toolCallId, JsonNode result) {
        return toolResult(toolName, toolCallId, result, null, null);
    }

    /**
     * Шорткат для TOOL_RESULT meta: payload = { toolName, toolCallId, result?, error? }
     * с опциональным trace.
     *
     * <p>Если инструмент упал — передаётся {@code error}, {@code result} остаётся
     * {@code null}. Сериализуется как сообщение с ролью {@code TOOL}.
     */
    public ObjectNode toolResult(
            String toolName,
            String toolCallId,
            JsonNode result,
            String error,
            MessageTraceDto trace
    ) {
        MessageToolResultPayloadDto payload = MessageToolResultPayloadDto.builder()
                .toolName(toolName != null ? toolName : "")
                .toolCallId(toolCallId)
                .result(result)
                .error(error)
                .build();
        return envelope(MessageMetaType.TOOL_RESULT, payload, trace);
    }

    /**
     * Шорткат для item-блока {@code kind = "text"}.
     *
     * <p>Используется для нарративных текстовых блоков внутри составных
     * ответов ({@code meta.type = cards}, items[] = [text, game, game, ...]).
     */
    public MessageTextItemDto textItem(String text) {
        return MessageTextItemDto.builder()
                .text(text != null ? text : "")
                .build();
    }

    /**
     * Создает DTO trace-объекта (requestId/runId).
     */
    public MessageTraceDto trace(String requestId, String runId) {
        return new MessageTraceDto(requestId, runId);
    }
}
