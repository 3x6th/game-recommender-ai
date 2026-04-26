package ru.perevalov.gamerecommenderai.message;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.perevalov.gamerecommenderai.constant.ChatLimits;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

/**
 * Валидатор бизнес-правил для chat_messages.
 * Отвечает за ограничения, пустоту сообщений и допустимые meta.type.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageValidator {

    private static final int MAX_CONTENT_LENGTH = ChatLimits.MAX_CONTENT_LENGTH;
    private static final int MAX_META_BYTES = ChatLimits.MAX_META_BYTES;

    private static final Set<MessageMetaType> ASSISTANT_ALLOWED_TYPES = EnumSet.of(
            MessageMetaType.REPLY,
            MessageMetaType.CARDS,
            MessageMetaType.MIXED,
            MessageMetaType.STATUS,
            MessageMetaType.ERROR
    );

    private final MessageMetaValidator messageMetaValidator;
    private final ObjectMapper objectMapper;

    /**
     * Проверяет сообщение перед сохранением.
     */
    public void validateForAppend(UUID chatId, MessageRole role, String content, JsonNode meta) {
        validateChatId(chatId);
        validateRole(role);
        validateContentLength(content);
        messageMetaValidator.validateOrThrow(meta);
        validateRoleMetaType(role, meta);
        validateNotEmpty(content, meta);
        validateMetaSize(meta);
    }

    /**
     * Извлекает clientRequestId из reply payload (если есть).
     */
    public UUID extractClientRequestId(JsonNode meta) {
        if (meta == null || !meta.isObject()) {
            return null;
        }
        MessageMetaType type = getMetaType(meta);
        if (type != MessageMetaType.REPLY) {
            return null;
        }
        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode idNode = payload.get(MessageMetaFields.REPLY_CLIENT_REQUEST_ID);
        if (idNode == null || !idNode.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(idNode.asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void validateChatId(UUID chatId) {
        if (chatId == null) {
            throw new GameRecommenderException(ErrorType.INVALID_CHAT_MESSAGE, "chatId is required");
        }
    }

    private void validateRole(MessageRole role) {
        if (role == null) {
            throw new GameRecommenderException(ErrorType.INVALID_CHAT_MESSAGE, "role is required");
        }
    }

    private void validateContentLength(String content) {
        if (content == null) {
            return;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE, "content exceeds max length");
        }
    }

    private void validateRoleMetaType(MessageRole role, JsonNode meta) {
        if (role != MessageRole.ASSISTANT) {
            return;
        }
        MessageMetaType type = getMetaType(meta);
        if (type == null || !ASSISTANT_ALLOWED_TYPES.contains(type)) {
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE, "assistant meta.type is invalid");
        }
    }

    private void validateNotEmpty(String content, JsonNode meta) {
        boolean contentEmpty = isBlank(content);
        MessageMetaType type = getMetaType(meta);

        if (type == MessageMetaType.REPLY) {
            boolean replyTextEmpty = isReplyTextEmpty(meta);
            if (contentEmpty && replyTextEmpty) {
                throw new GameRecommenderException(
                        ErrorType.INVALID_CHAT_MESSAGE, "content and reply text are empty");
            }
            return;
        }

        if (type == MessageMetaType.STATUS) {
            boolean stateEmpty = isStatusStateEmpty(meta);
            if (contentEmpty && stateEmpty) {
                throw new GameRecommenderException(
                        ErrorType.INVALID_CHAT_MESSAGE, "content and status.state are empty");
            }
            return;
        }

        if (type == MessageMetaType.ERROR) {
            boolean errorEmpty = isErrorPayloadEmpty(meta);
            if (contentEmpty && errorEmpty) {
                throw new GameRecommenderException(
                        ErrorType.INVALID_CHAT_MESSAGE, "content and error payload are empty");
            }
            return;
        }

        boolean payloadEmpty = isPayloadEmpty(meta);
        if (contentEmpty && payloadEmpty) {
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE, "content and payload are empty");
        }
    }

    private void validateMetaSize(JsonNode meta) {
        try {
            int size = objectMapper.writeValueAsBytes(meta).length;
            if (size > MAX_META_BYTES) {
                throw new GameRecommenderException(
                        ErrorType.INVALID_CHAT_MESSAGE, "meta exceeds max size");
            }
        } catch (Exception e) {
            log.warn("Failed to serialize meta for size check", e);
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE, "failed to serialize meta");
        }
    }

    private boolean isPayloadEmpty(JsonNode meta) {
        if (meta == null || !meta.isObject()) {
            return true;
        }
        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        if (payload == null || payload.isNull()) {
            return true;
        }
        if (payload.isObject() || payload.isArray()) {
            return payload.size() == 0;
        }
        if (payload.isTextual()) {
            return payload.asText().trim().isEmpty();
        }
        return false;
    }

    private MessageMetaType getMetaType(JsonNode meta) {
        if (meta == null || !meta.isObject()) {
            return null;
        }
        JsonNode typeNode = meta.get(MessageMetaFields.FIELD_TYPE);
        if (typeNode == null || !typeNode.isTextual()) {
            return null;
        }
        try {
            return MessageMetaType.fromWireName(typeNode.asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isReplyTextEmpty(JsonNode meta) {
        JsonNode payload = meta != null ? meta.get(MessageMetaFields.FIELD_PAYLOAD) : null;
        if (payload == null || !payload.isObject()) {
            return true;
        }
        JsonNode text = payload.get(MessageMetaFields.REPLY_TEXT);
        return text == null || !text.isTextual() || text.asText().trim().isEmpty();
    }

    private boolean isStatusStateEmpty(JsonNode meta) {
        JsonNode payload = meta != null ? meta.get(MessageMetaFields.FIELD_PAYLOAD) : null;
        if (payload == null || !payload.isObject()) {
            return true;
        }
        JsonNode state = payload.get(MessageMetaFields.STATUS_STATE);
        return state == null || !state.isTextual() || state.asText().trim().isEmpty();
    }

    private boolean isErrorPayloadEmpty(JsonNode meta) {
        JsonNode payload = meta != null ? meta.get(MessageMetaFields.FIELD_PAYLOAD) : null;
        if (payload == null || !payload.isObject()) {
            return true;
        }
        JsonNode code = payload.get(MessageMetaFields.ERROR_CODE);
        JsonNode message = payload.get(MessageMetaFields.ERROR_MESSAGE);
        JsonNode retryable = payload.get(MessageMetaFields.ERROR_RETRYABLE);
        boolean codeEmpty = code == null || !code.isTextual() || code.asText().trim().isEmpty();
        boolean messageEmpty = message == null || !message.isTextual() || message.asText().trim().isEmpty();
        boolean retryableEmpty = retryable == null || !retryable.isBoolean();
        return codeEmpty && messageEmpty && retryableEmpty;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
