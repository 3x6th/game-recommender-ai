package ru.perevalov.gamerecommenderai.util;

import lombok.experimental.UtilityClass;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@UtilityClass
public class TimeHelper {

    /**
     * Конвертирует {@link LocalDateTime} в {@link Instant} в системном часовом поясе.
     *
     * @param localDateTime дата и время без привязки к зоне; {@code null} допустим
     * @return {@link Instant} или {@code null}, если передан {@code null}
     */
    public static Instant toSystemTimezoneInstant(LocalDateTime localDateTime) {
        return localDateTime != null ? localDateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * Parses a chat-history cursor from clients.
     * <p>
     * Preferred format is an ISO-8601 instant/offset timestamp, for example
     * {@code 2026-04-29T21:50:29.792Z}. Legacy offset-less values are still
     * accepted and interpreted in the backend system timezone.
     *
     * @param value cursor timestamp from the {@code before} query parameter
     * @return parsed {@link Instant}
     */
    public static Instant parseClientCursorInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE, "before timestamp is required");
        }

        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Try offset and legacy local formats below.
        }

        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try legacy local format below.
        }

        try {
            return toSystemTimezoneInstant(LocalDateTime.parse(trimmed));
        } catch (DateTimeParseException ex) {
            throw new GameRecommenderException(
                    ErrorType.INVALID_CHAT_MESSAGE,
                    "before must be an ISO-8601 timestamp");
        }
    }
}
