package ru.perevalov.gamerecommenderai.dto;

/**
 * Semantic chat message passed to the AI service as bounded short-term context.
 */
public record AiChatHistoryMessage(Role role, String text) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
