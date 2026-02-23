package ru.perevalov.gamerecommenderai.service;

import java.util.UUID;

import ru.perevalov.gamerecommenderai.security.model.UserRole;

/**
 * Контекст запроса для chat-сценариев: владелец и опциональный AI-агент.
 */
public record RequestContext(
        UUID userId,
        String sessionId,
        Long steamId,
        UserRole role,
        UUID aiAgentId
) {
    /**
     * Контекст для авторизованного пользователя.
     */
    public static RequestContext forUser(UUID userId, Long steamId, UUID aiAgentId) {
        return new RequestContext(userId, null, steamId, UserRole.USER, aiAgentId);
    }

    /**
     * Контекст для гостя по sessionId.
     */
    public static RequestContext forGuest(String sessionId, UUID aiAgentId) {
        return new RequestContext(null, sessionId, null, UserRole.GUEST, aiAgentId);
    }

    /**
     * Признак контекста пользователя.
     */
    public boolean isUser() {
        return userId != null;
    }

    /**
     * Признак гостевого контекста.
     */
    public boolean isGuest() {
        return userId == null && sessionId != null && !sessionId.isBlank();
    }
}
