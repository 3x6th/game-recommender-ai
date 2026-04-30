package ru.perevalov.gamerecommenderai.pipeline;

/**
 * Константы порядка выполнения шагов recommendation pipeline.
 */
public final class PipelineStepOrder {
    public static final int CONTEXT_RESOLVER = 100;
    public static final int CHAT_RESOLVER = 200;
    public static final int PERSIST_USER_MESSAGE = 300;
    public static final int AI_CALL = 400;
    public static final int PERSIST_ASSISTANT = 500;
    public static final int TOUCH_CHAT = 600;
    public static final int RESPONSE = 700;

    /**
     * Запрещает создание экземпляра класса с константами.
     */
    private PipelineStepOrder() {
    }
}
