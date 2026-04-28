package ru.perevalov.gamerecommenderai.message;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.perevalov.gamerecommenderai.exception.InvalidMetaException;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;

public class MessageMetaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory factory = new MessageMetaFactory(objectMapper);
    private final MessageMetaValidator validator = new MessageMetaValidator();

    @Test
    void validate_validMetaFromFactory_thenNoErrorsOrWarnings() {
        MessageCardDto card = MessageCardDto.builder()
                .title("Game")
                .genre("RPG")
                .description("Story-driven RPG")
                .whyRecommended("Подходит под запрос")
                .platforms(List.of("PC"))
                .build();
        ObjectNode meta = factory.cards(List.of(card), null);

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void validateOrThrow_whenMetaNull_thenThrows() {
        Assertions.assertThatThrownBy(() -> validator.validateOrThrow(null))
                .isInstanceOf(InvalidMetaException.class)
                .hasMessageContaining("meta is null");
    }

    @Test
    void validate_whenMetaNotObject_thenError() {
        MessageMetaValidationResult result = validator.validate(objectMapper.createArrayNode());

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("meta must be a JSON object"));
    }

    @Test
    void validate_whenSchemaVersionMissing_thenError() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_TYPE, "reply");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("schemaVersion is required"));
    }

    @Test
    void validate_whenTypeBlank_thenError() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "   ");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("type must not be blank"));
    }

    @Test
    void validate_whenPayloadMissing_thenError() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "reply");

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("payload is required"));
    }

    @Test
    void validate_whenUnknownType_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "unknown");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("Unknown meta.type"));
    }

    @Test
    void validate_whenSchemaVersionNotOne_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, 2);
        meta.put(MessageMetaFields.FIELD_TYPE, "reply");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("schemaVersion is not 1"));
    }

    @Test
    void validate_whenTraceInvalid_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "reply");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());
        meta.put(MessageMetaFields.FIELD_TRACE, "bad");

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("trace must be an object"));
    }

    @Test
    void validate_whenTraceFieldsNotStrings_thenWarnsButValid() {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put(MessageMetaFields.TRACE_REQUEST_ID, 123);
        trace.put(MessageMetaFields.TRACE_RUN_ID, true);

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put(MessageMetaFields.FIELD_SCHEMA_VERSION, MessageMetaFields.SCHEMA_VERSION);
        meta.put(MessageMetaFields.FIELD_TYPE, "reply");
        meta.set(MessageMetaFields.FIELD_PAYLOAD, objectMapper.createObjectNode());
        meta.set(MessageMetaFields.FIELD_TRACE, trace);

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("trace.requestId must be a string"));
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("trace.runId must be a string"));
    }
}
