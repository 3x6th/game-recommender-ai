package ru.perevalov.gamerecommenderai.dto.message;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessageMetaFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory factory = new MessageMetaFactory(objectMapper);

    @Test
    void envelope_whenPayloadNull_thenUsesEmptyObjectAndNoTrace() {
        ObjectNode meta = factory.envelope(MessageMetaType.REPLY, null);

        Assertions.assertThat(meta.get("schemaVersion").asInt()).isEqualTo(1);
        Assertions.assertThat(meta.get("type").asText()).isEqualTo("reply");
        Assertions.assertThat(meta.get("payload").isObject()).isTrue();
        Assertions.assertThat(meta.has("trace")).isFalse();
    }

    @Test
    void envelope_whenTraceProvided_thenTraceIsSet() {
        ObjectNode trace = factory.trace("req-1", "run-1");
        ObjectNode meta = factory.envelope(MessageMetaType.REPLY, objectMapper.createObjectNode(), trace);

        Assertions.assertThat(meta.get("trace")).isEqualTo(trace);
        Assertions.assertThat(meta.get("trace").get("requestId").asText()).isEqualTo("req-1");
        Assertions.assertThat(meta.get("trace").get("runId").asText()).isEqualTo("run-1");
    }

    @Test
    void status_whenCalled_thenBuildsStatusPayload() {
        ObjectNode meta = factory.status("thinking");

        Assertions.assertThat(meta.get("type").asText()).isEqualTo("status");
        Assertions.assertThat(meta.get("payload").get("state").asText()).isEqualTo("thinking");
    }

    @Test
    void cards_whenItemsNull_thenCreatesEmptyArray() {
        ObjectNode meta = factory.cards(null);

        Assertions.assertThat(meta.get("type").asText()).isEqualTo("cards");
        Assertions.assertThat(meta.get("payload").get("items").isArray()).isTrue();
        Assertions.assertThat(meta.get("payload").get("items").size()).isEqualTo(0);
    }

    @Test
    void error_whenCalled_thenBuildsErrorPayload() {
        ObjectNode meta = factory.error("TIMEOUT", "Service timeout", true);

        JsonNode payload = meta.get("payload");
        Assertions.assertThat(meta.get("type").asText()).isEqualTo("error");
        Assertions.assertThat(payload.get("code").asText()).isEqualTo("TIMEOUT");
        Assertions.assertThat(payload.get("message").asText()).isEqualTo("Service timeout");
        Assertions.assertThat(payload.get("retryable").asBoolean()).isTrue();
    }

    @Test
    void mixed_whenExtraNull_thenOmitsExtra() {
        ObjectNode meta = factory.mixed("hello", null, null);

        JsonNode payload = meta.get("payload");
        Assertions.assertThat(meta.get("type").asText()).isEqualTo("mixed");
        Assertions.assertThat(payload.get("text").asText()).isEqualTo("hello");
        Assertions.assertThat(payload.get("items").isArray()).isTrue();
        Assertions.assertThat(payload.has("extra")).isFalse();
    }

    @Test
    void trace_whenOnlyRequestId_thenContainsOnlyRequestId() {
        ObjectNode trace = factory.trace("req-2", null);

        Assertions.assertThat(trace.get("requestId").asText()).isEqualTo("req-2");
        Assertions.assertThat(trace.has("runId")).isFalse();
    }
}
