package ru.perevalov.gamerecommenderai.message;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReasoningItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageTextItemDto;
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
    void cards_whenReasoningItemFirst_thenSerializedWithKindReasoning() {
        MessageReasoningItemDto reasoning = MessageReasoningItemDto.builder()
                .text("Подобрал RPG с нарративом")
                .build();
        MessageCardDto card = MessageCardDto.builder()
                .title("Game")
                .genre("RPG")
                .description("Story-driven RPG")
                .whyRecommended("Подходит под запрос юзера")
                .platforms(List.of("PC"))
                .build();

        ObjectNode meta = factory.cards(List.of(reasoning, card));

        JsonNode items = meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS);
        Assertions.assertThat(items.size()).isEqualTo(2);

        Assertions.assertThat(items.get(0).get(MessageMetaFields.ITEM_KIND).asText())
                .isEqualTo(MessageMetaFields.ITEM_KIND_REASONING);
        Assertions.assertThat(items.get(0).get(MessageMetaFields.ITEM_TEXT).asText())
                .isEqualTo("Подобрал RPG с нарративом");

        Assertions.assertThat(items.get(1).get(MessageMetaFields.ITEM_KIND).asText())
                .isEqualTo(MessageMetaFields.ITEM_KIND_GAME);
        Assertions.assertThat(items.get(1).get(MessageMetaFields.CARD_TITLE).asText()).isEqualTo("Game");
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
    void trace_whenOnlyRequestId_thenContainsOnlyRequestId() {
        MessageTraceDto trace = factory.trace("req-2", null);

        Assertions.assertThat(trace.getRequestId()).isEqualTo("req-2");
        Assertions.assertThat(trace.getRunId()).isNull();
    }

    @Test
    void cards_whenTextItemMixedWithGame_thenBothKindsSerialized() {
        MessageTextItemDto text = MessageTextItemDto.builder()
                .text("Вот что я нашёл по твоему запросу.")
                .build();
        MessageCardDto card = MessageCardDto.builder()
                .title("Game")
                .genre("RPG")
                .description("desc")
                .whyRecommended("why")
                .platforms(List.of("PC"))
                .build();

        ObjectNode meta = factory.cards(List.of(text, card));

        JsonNode items = meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS);
        Assertions.assertThat(items.size()).isEqualTo(2);
        Assertions.assertThat(items.get(0).get(MessageMetaFields.ITEM_KIND).asText())
                .isEqualTo(MessageMetaFields.ITEM_KIND_TEXT);
        Assertions.assertThat(items.get(0).get(MessageMetaFields.ITEM_TEXT).asText())
                .isEqualTo("Вот что я нашёл по твоему запросу.");
        Assertions.assertThat(items.get(1).get(MessageMetaFields.ITEM_KIND).asText())
                .isEqualTo(MessageMetaFields.ITEM_KIND_GAME);
    }

    @Test
    void textItem_whenNullText_thenUsesEmpty() {
        MessageTextItemDto item = factory.textItem(null);

        Assertions.assertThat(item.getText()).isEqualTo("");
    }

    @Test
    void toolCall_whenCalled_thenBuildsToolCallPayload() {
        ObjectNode args = objectMapper.createObjectNode().put("query", "hades");

        ObjectNode meta = factory.toolCall("steam_search", args, "call-42");

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("tool_call");
        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_CALL_NAME).asText()).isEqualTo("steam_search");
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_CALL_ID).asText()).isEqualTo("call-42");
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_CALL_ARGS).get("query").asText()).isEqualTo("hades");
    }

    @Test
    void toolResult_whenSuccess_thenResultSetAndErrorOmitted() {
        JsonNode result = objectMapper.createObjectNode().put("count", 5);

        ObjectNode meta = factory.toolResult("steam_search", "call-42", result);

        Assertions.assertThat(meta.get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("tool_result");
        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_RESULT_NAME).asText()).isEqualTo("steam_search");
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_RESULT_CALL_ID).asText()).isEqualTo("call-42");
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_RESULT_RESULT).get("count").asInt()).isEqualTo(5);
        Assertions.assertThat(payload.has(MessageMetaFields.TOOL_RESULT_ERROR)).isFalse();
    }

    @Test
    void toolResult_whenError_thenErrorSetAndResultOmitted() {
        ObjectNode meta = factory.toolResult("steam_search", "call-42", null, "upstream timeout", null);

        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        Assertions.assertThat(payload.get(MessageMetaFields.TOOL_RESULT_ERROR).asText()).isEqualTo("upstream timeout");
        Assertions.assertThat(payload.has(MessageMetaFields.TOOL_RESULT_RESULT)).isFalse();
    }

    @Test
    void cards_whenItemsProvided_thenSerialized() {
        MessageCardDto card = MessageCardDto.builder()
                .title("Forza Horizon 5")
                .genre("Racing, Open World")
                .description("Open-world racing in Mexico")
                .whyRecommended("Short relaxing sessions")
                .platforms(List.of("PC", "Xbox Series X/S"))
                .rating(9.2)
                .releaseYear("2021")
                .matchScore(0.93)
                .build();
        ObjectNode meta = factory.cards(List.of(card));

        JsonNode items = meta.get(MessageMetaFields.FIELD_PAYLOAD).get(MessageMetaFields.CARDS_ITEMS);
        Assertions.assertThat(items.isArray()).isTrue();
        Assertions.assertThat(items.size()).isEqualTo(1);

        JsonNode item = items.get(0);
        Assertions.assertThat(item.get(MessageMetaFields.ITEM_KIND).asText())
                .isEqualTo(MessageMetaFields.ITEM_KIND_GAME);
        Assertions.assertThat(item.get(MessageMetaFields.CARD_TITLE).asText()).isEqualTo("Forza Horizon 5");
        Assertions.assertThat(item.get(MessageMetaFields.CARD_GENRE).asText()).isEqualTo("Racing, Open World");
        Assertions.assertThat(item.get(MessageMetaFields.CARD_DESCRIPTION).asText())
                .isEqualTo("Open-world racing in Mexico");
        Assertions.assertThat(item.get(MessageMetaFields.CARD_WHY_RECOMMENDED).asText())
                .isEqualTo("Short relaxing sessions");
        Assertions.assertThat(item.get(MessageMetaFields.CARD_PLATFORMS).isArray()).isTrue();
        Assertions.assertThat(item.get(MessageMetaFields.CARD_PLATFORMS).size()).isEqualTo(2);
        Assertions.assertThat(item.get(MessageMetaFields.CARD_RATING).asDouble()).isEqualTo(9.2);
        Assertions.assertThat(item.get(MessageMetaFields.CARD_RELEASE_YEAR).asText()).isEqualTo("2021");
        Assertions.assertThat(item.get(MessageMetaFields.CARD_MATCH_SCORE).asDouble()).isEqualTo(0.93);
    }
}
