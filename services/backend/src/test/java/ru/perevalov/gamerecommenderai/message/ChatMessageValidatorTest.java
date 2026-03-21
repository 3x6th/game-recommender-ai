package ru.perevalov.gamerecommenderai.message;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;

class ChatMessageValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory metaFactory = new MessageMetaFactory(objectMapper);
    private final MessageMetaValidator metaValidator = new MessageMetaValidator();
    private final ChatMessageValidator validator = new ChatMessageValidator(metaValidator, objectMapper);

    @Test
    void validateForAppend_whenAssistantUnknownType_thenFails() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "tool_call");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());

        assertThatThrownBy(() ->
                validator.validateForAppend(UUID.randomUUID(), MessageRole.ASSISTANT, "hi", meta)
        ).isInstanceOf(GameRecommenderException.class)
                .extracting(ex -> ((GameRecommenderException) ex).getErrorType())
                .isEqualTo(ErrorType.INVALID_CHAT_MESSAGE);
    }

    @Test
    void validateForAppend_whenReplyEmptyAndContentBlank_thenFails() {
        ObjectNode meta = metaFactory.reply("");

        assertThatThrownBy(() ->
                validator.validateForAppend(UUID.randomUUID(), MessageRole.USER, "   ", meta)
        ).isInstanceOf(GameRecommenderException.class)
                .extracting(ex -> ((GameRecommenderException) ex).getErrorType())
                .isEqualTo(ErrorType.INVALID_CHAT_MESSAGE);
    }

    @Test
    void validateForAppend_whenValidReply_thenOk() {
        ObjectNode meta = metaFactory.reply("hello");

        validator.validateForAppend(UUID.randomUUID(), MessageRole.USER, "hello", meta);
    }

    @Test
    void validateForAppend_whenCardsWithReasoning_thenOk() {
        MessageCardDto card = new MessageCardDto("steam:1", "Game", null, null, null, null, null);
        String reasoning = "Тестовое объяснение, почему выбраны эти игры";
        ObjectNode meta = metaFactory.cards(List.of(card), reasoning);

        validator.validateForAppend(UUID.randomUUID(), MessageRole.ASSISTANT, "Вот рекомендации", meta);
    }

    @Test
    void extractClientRequestId_whenPresent_thenReturnsValue() {
        UUID requestId = UUID.randomUUID();
        ObjectNode meta = metaFactory.reply("hi", requestId, null, null);

        assertThat(validator.extractClientRequestId(meta)).isEqualTo(requestId);
    }
}
