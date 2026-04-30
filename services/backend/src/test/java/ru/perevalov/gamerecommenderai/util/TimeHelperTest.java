package ru.perevalov.gamerecommenderai.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

class TimeHelperTest {

    @Test
    void parseClientCursorInstant_whenInstantWithZ_thenReturnsSameInstant() {
        Instant instant = Instant.parse("2026-04-29T21:50:29.792Z");

        assertThat(TimeHelper.parseClientCursorInstant("2026-04-29T21:50:29.792Z"))
                .isEqualTo(instant);
    }

    @Test
    void parseClientCursorInstant_whenOffsetDateTime_thenReturnsInstant() {
        assertThat(TimeHelper.parseClientCursorInstant("2026-04-30T00:50:29.792+03:00"))
                .isEqualTo(Instant.parse("2026-04-29T21:50:29.792Z"));
    }

    @Test
    void parseClientCursorInstant_whenLegacyLocalDateTime_thenUsesSystemTimezone() {
        LocalDateTime localDateTime = LocalDateTime.of(2026, 4, 30, 0, 50, 29, 792_000_000);

        assertThat(TimeHelper.parseClientCursorInstant("2026-04-30T00:50:29.792"))
                .isEqualTo(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void parseClientCursorInstant_whenInvalid_thenThrowsBadRequestError() {
        assertThatThrownBy(() -> TimeHelper.parseClientCursorInstant("not-a-date"))
                .isInstanceOf(GameRecommenderException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.INVALID_CHAT_MESSAGE);
    }
}
