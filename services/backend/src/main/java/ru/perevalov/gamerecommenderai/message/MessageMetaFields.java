package ru.perevalov.gamerecommenderai.message;

/**
 * Глобальные ключи JSON-структуры meta (используются фабрикой, валидатором и DTO).
 */
public final class MessageMetaFields {
    public static final int SCHEMA_VERSION = 1;

    public static final String REASONING = "reasoning";

    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_PAYLOAD = "payload";
    public static final String FIELD_TRACE = "trace";

    public static final String TRACE_REQUEST_ID = "requestId";
    public static final String TRACE_RUN_ID = "runId";

    public static final String STATUS_STATE = "state";

    public static final String CARDS_ITEMS = "items";

    public static final String ERROR_CODE = "code";
    public static final String ERROR_MESSAGE = "message";
    public static final String ERROR_RETRYABLE = "retryable";

    public static final String MIXED_TEXT = "text";
    public static final String MIXED_ITEMS = "items";
    public static final String MIXED_EXTRA = "extra";

    public static final String REPLY_TEXT = "text";
    public static final String REPLY_CLIENT_REQUEST_ID = "clientRequestId";
    public static final String REPLY_TAGS = "tags";
    public static final String REPLY_EXTRA = "extra";

    public static final String CARD_TITLE = "title";
    public static final String CARD_GENRE = "genre";
    public static final String CARD_DESCRIPTION = "description";
    public static final String CARD_WHY_RECOMMENDED = "whyRecommended";
    public static final String CARD_PLATFORMS = "platforms";
    public static final String CARD_RATING = "rating";
    public static final String CARD_RELEASE_YEAR = "releaseYear";
    public static final String CARD_TAGS = "tags";
    public static final String CARD_MATCH_SCORE = "matchScore";

    private MessageMetaFields() {
    }
}
