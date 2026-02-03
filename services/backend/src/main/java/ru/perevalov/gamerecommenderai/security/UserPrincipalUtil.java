package ru.perevalov.gamerecommenderai.security;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Component
public class UserPrincipalUtil {

    /** Метод возвращает String стим id, если пользователь зарегистрирован или "GUEST", если нет
     */
    public Mono<String> getSteamIdFromSecurityContext() {
        return Mono.deferContextual(ctx -> {
            RequestIdentity identity = ctx.getOrDefault(RequestIdentity.class, RequestIdentity.anonymous());
            return Mono.justOrEmpty(identity.steamId() != null ? identity.steamId().toString() : null);
        });
    }

    public Mono<UserRole> getCurrentUserRole() {
        return Mono.deferContextual(ctx -> {
            RequestIdentity identity = ctx.getOrDefault(RequestIdentity.class, RequestIdentity.anonymous());
            return Mono.just(identity.role());
        });
    }
}
