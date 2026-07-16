package ru.perevalov.gamerecommenderai.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdUtilsTest {

    @Test
    void normalizeOrNull_sanitizesAndBoundsExternalValue() {
        String value = " trace id\n" + "x".repeat(200);

        String normalized = RequestIdUtils.normalizeOrNull(value);

        assertThat(normalized)
                .hasSize(128)
                .startsWith("trace_id_x")
                .doesNotContain("\n", " ");
    }

    @Test
    void normalizeOrDefault_usesFallbackForMissingValue() {
        assertThat(RequestIdUtils.normalizeOrDefault(" ", "unknown"))
                .isEqualTo("unknown");
    }
}
