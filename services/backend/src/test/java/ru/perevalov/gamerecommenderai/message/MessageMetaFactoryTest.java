package ru.perevalov.gamerecommenderai.message;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageTraceDto;

public class MessageMetaFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory factory = new MessageMetaFactory(objectMapper);

    @Test
    void envelope_whenPayloadNull_thenUsesEmptyObjectAndNoTrace() {
        ObjectNode meta = factory.envelope(MessageMetaType.REPLY, null);

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_SCHEMA_VERSION).asInt()).isEqualTo(1);
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("reply");
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_PAYLOAD).isObject()).isTrue();
        Assertions.assertThat(meta.has(MessageMetaFields.FIELD_TRACE)).isFalse();
    }

    @Test
    void envelope_whenTraceProvided_thenTraceIsSet() {
        MessageTraceDto trace = factory.trace("req-1", "run-1");
        ObjectNode meta = factory.envelope(MessageMetaType.REPLY, objectMapper.createObjectNode(), trace);

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TRACE).get(MessageMetaFields.TRACE_REQUEST_ID).asText())
                .isEqualTo("req-1");
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TRACE).get(MessageMetaFields.TRACE_RUN_ID).asText())
                .isEqualTo("run-1");
    }

    @Test
    void status_whenCalled_thenBuildsStatusPayload() {
        ObjectNode meta = factory.status("thinking");

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("status");
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.STATUS_STATE).asText())
                .isEqualTo("thinking");
    }

    @Test
    void cards_whenItemsNull_thenCreatesEmptyArray() {
        ObjectNode meta = factory.cards(null);

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("cards");
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS).isArray())
                .isTrue();
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS).size())
                .isEqualTo(0);
    }

    @Test
    void error_whenCalled_thenBuildsErrorPayload() {
        ObjectNode meta = factory.error("TIMEOUT", "Service timeout", true);

        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("error");
        Assertions.assertThat(payload.get(MessageMetaFields.ERROR_CODE).asText()).isEqualTo("TIMEOUT");
        Assertions.assertThat(payload.get(MessageMetaFields.ERROR_MESSAGE).asText()).isEqualTo("Service timeout");
        Assertions.assertThat(payload.get(MessageMetaFields.ERROR_RETRYABLE).asBoolean()).isTrue();
    }

    @Test
    void mixed_whenExtraNull_thenOmitsExtra() {
        ObjectNode meta = factory.mixed("hello", null, null);

        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("mixed");
        Assertions.assertThat(payload.get(MessageMetaFields.MIXED_TEXT).asText()).isEqualTo("hello");
        Assertions.assertThat(payload.get(MessageMetaFields.MIXED_ITEMS).isArray()).isTrue();
        Assertions.assertThat(payload.has(MessageMetaFields.MIXED_EXTRA)).isFalse();
    }

    @Test
    void trace_whenOnlyRequestId_thenContainsOnlyRequestId() {
        MessageTraceDto trace = factory.trace("req-2", null);

        Assertions.assertThat(trace.getRequestId()).isEqualTo("req-2");
        Assertions.assertThat(trace.getRunId()).isNull();
    }

    @Test
    void cards_whenItemsProvided_thenSerialized() {
        MessageCardDto card = new MessageCardDto("steam:1", "Game", 0.5, null, null, null, null);
        ObjectNode meta = factory.cards(List.of(card));

        JsonNode items = meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS);
        Assertions.assertThat(items.isArray()).isTrue();
        Assertions.assertThat(items.size()).isEqualTo(1);
        Assertions.assertThat(items.get(0).get(MessageMetaFields.CARD_GAME_ID).asText()).isEqualTo("steam:1");
    }
}
