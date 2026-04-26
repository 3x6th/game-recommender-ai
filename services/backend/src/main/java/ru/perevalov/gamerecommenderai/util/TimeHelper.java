package ru.perevalov.gamerecommenderai.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
}
