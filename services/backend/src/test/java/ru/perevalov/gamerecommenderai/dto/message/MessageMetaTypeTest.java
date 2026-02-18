package ru.perevalov.gamerecommenderai.dto.message;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessageMetaTypeTest {

    @Test
    void givenEnumValue_whenWireName_thenReturnsExpectedWireValue() {
        Assertions.assertThat(MessageMetaType.REPLY.wireName()).isEqualTo("reply");
        Assertions.assertThat(MessageMetaType.CARDS.wireName()).isEqualTo("cards");
        Assertions.assertThat(MessageMetaType.MIXED.wireName()).isEqualTo("mixed");
        Assertions.assertThat(MessageMetaType.STATUS.wireName()).isEqualTo("status");
        Assertions.assertThat(MessageMetaType.ERROR.wireName()).isEqualTo("error");
        Assertions.assertThat(MessageMetaType.TOOL_CALL.wireName()).isEqualTo("tool_call");
        Assertions.assertThat(MessageMetaType.TOOL_RESULT.wireName()).isEqualTo("tool_result");
    }

    @Test
    void givenWireName_whenFromWireName_thenReturnsExpectedEnum() {
        Assertions.assertThat(MessageMetaType.fromWireName("reply")).isEqualTo(MessageMetaType.REPLY);
        Assertions.assertThat(MessageMetaType.fromWireName("cards")).isEqualTo(MessageMetaType.CARDS);
        Assertions.assertThat(MessageMetaType.fromWireName("mixed")).isEqualTo(MessageMetaType.MIXED);
        Assertions.assertThat(MessageMetaType.fromWireName("status")).isEqualTo(MessageMetaType.STATUS);
        Assertions.assertThat(MessageMetaType.fromWireName("error")).isEqualTo(MessageMetaType.ERROR);
        Assertions.assertThat(MessageMetaType.fromWireName("tool_call")).isEqualTo(MessageMetaType.TOOL_CALL);
        Assertions.assertThat(MessageMetaType.fromWireName("tool_result")).isEqualTo(MessageMetaType.TOOL_RESULT);
    }

    @Test
    void givenUnknownWireName_whenFromWireName_thenThrowsReadableError() {
        Assertions.assertThatThrownBy(() -> MessageMetaType.fromWireName("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown MessageMetaType wireName: 'unknown'")
                .hasMessageContaining("reply")
                .hasMessageContaining("cards")
                .hasMessageContaining("mixed")
                .hasMessageContaining("status")
                .hasMessageContaining("error")
                .hasMessageContaining("tool_call")
                .hasMessageContaining("tool_result");
    }
}
