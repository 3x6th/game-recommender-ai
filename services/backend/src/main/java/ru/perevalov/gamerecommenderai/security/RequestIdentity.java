package ru.perevalov.gamerecommenderai.security;

import java.util.Optional;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

public record RequestIdentity(String sessionId, UserRole role, Long steamId) {
    public static final String EXCHANGE_ATTRIBUTE = RequestIdentity.class.getName();

    public static RequestIdentity guest(String sessionId) {
        return new RequestIdentity(sessionId, UserRole.GUEST, null);
    }

    public static RequestIdentity anonymous() {
        return new RequestIdentity(null, UserRole.GUEST, null);
    }

    public Optional<String> sessionIdOptional() {
        return Optional.ofNullable(sessionId);
    }

    public Optional<Long> steamIdOptional() {
        return Optional.ofNullable(steamId);
    }
}
