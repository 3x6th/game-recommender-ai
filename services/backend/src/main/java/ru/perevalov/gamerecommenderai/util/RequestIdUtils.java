package ru.perevalov.gamerecommenderai.util;

import java.util.regex.Pattern;

/**
 * Normalizes external correlation identifiers before metadata and log usage.
 */
public final class RequestIdUtils {

    private static final int MAX_REQUEST_ID_CHARS = 128;
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9._:-]");

    private RequestIdUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String normalizeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = UNSAFE_CHARS.matcher(value.trim()).replaceAll("_");
        if (normalized.length() > MAX_REQUEST_ID_CHARS) {
            normalized = normalized.substring(0, MAX_REQUEST_ID_CHARS);
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalizeOrNull(value);
        return normalized == null ? fallback : normalized;
    }
}
