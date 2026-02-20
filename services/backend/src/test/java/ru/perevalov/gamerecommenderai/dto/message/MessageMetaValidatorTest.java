package ru.perevalov.gamerecommenderai.dto.message;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.perevalov.gamerecommenderai.exception.InvalidMetaException;
import ru.perevalov.gamerecommenderai.message.MessageMetaFactory;
import ru.perevalov.gamerecommenderai.message.MessageMetaValidationResult;
import ru.perevalov.gamerecommenderai.message.MessageMetaValidator;

public class MessageMetaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory factory = new MessageMetaFactory(objectMapper);
    private final MessageMetaValidator validator = new MessageMetaValidator();

    @Test
    void validate_validMetaFromFactory_thenNoErrorsOrWarnings() {
        ObjectNode meta = factory.cards(objectMapper.createArrayNode());

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
        meta.put("type", "reply");
        meta.set("payload", objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("schemaVersion is required"));
    }

    @Test
    void validate_whenTypeBlank_thenError() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", 1);
        meta.put("type", "   ");
        meta.set("payload", objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("type must not be blank"));
    }

    @Test
    void validate_whenPayloadMissing_thenError() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", 1);
        meta.put("type", "reply");

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isFalse();
        Assertions.assertThat(result.getErrors()).anyMatch(m -> m.contains("payload is required"));
    }

    @Test
    void validate_whenUnknownType_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", 1);
        meta.put("type", "unknown");
        meta.set("payload", objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("Unknown meta.type"));
    }

    @Test
    void validate_whenSchemaVersionNotOne_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", 2);
        meta.put("type", "reply");
        meta.set("payload", objectMapper.createObjectNode());

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("schemaVersion is not 1"));
    }

    @Test
    void validate_whenTraceInvalid_thenWarnsButValid() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("schemaVersion", 1);
        meta.put("type", "reply");
        meta.set("payload", objectMapper.createObjectNode());
        meta.put("trace", "bad");

        MessageMetaValidationResult result = validator.validate(meta);

        Assertions.assertThat(result.isValid()).isTrue();
        Assertions.assertThat(result.getWarnings()).anyMatch(m -> m.contains("trace must be an object"));
    }
}
