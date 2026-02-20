package ru.perevalov.gamerecommenderai.message;

/**
 * Глобальные ключи JSON-структуры meta (используются фабрикой, валидатором и DTO).
 */
public final class MessageMetaFields {
    public static final int SCHEMA_VERSION = 1;

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

    public static final String CARD_GAME_ID = "gameId";
    public static final String CARD_TITLE = "title";
    public static final String CARD_SCORE = "score";
    public static final String CARD_REASON = "reason";
    public static final String CARD_TAGS = "tags";
    public static final String CARD_STORE_URL = "storeUrl";
    public static final String CARD_IMAGE_URL = "imageUrl";

    private MessageMetaFields() {
    }
}
