package ru.perevalov.gamerecommenderai.constant;

/**
 * Унифицированные ограничения для чатов и сообщений.
 * <p>
 * Используются на входе REST/pipeline ({@code ContextResolverStep},
 * Bean Validation в DTO) и на этапе персистентности ({@code ChatMessageValidator}),
 * чтобы избежать рассинхронизации проверок.
 */
public final class ChatLimits {

    /**
     * Максимальная длина текста сообщения ({@code content}) в символах.
     */
    public static final int MAX_CONTENT_LENGTH = 8000;

    /**
     * Максимальный размер сериализованного {@code meta} в байтах.
     * Жёсткий лимит на BE: 256KB.
     */
    public static final int MAX_META_BYTES = 256 * 1024;

    /**
     * Максимальное количество тегов в одном запросе.
     */
    public static final int MAX_TAGS = 32;

    /**
     * Максимальная длина одного тега в символах.
     */
    public static final int MAX_TAG_LENGTH = 64;

    /**
     * Максимальная длина {@code clientRequestId} / {@code chatId} / {@code steamId} в символах.
     * Ограничивает размер строковых идентификаторов в теле запроса.
     */
    public static final int MAX_ID_LENGTH = 64;

    private ChatLimits() {
        throw new UnsupportedOperationException("Utility class");
    }
}
