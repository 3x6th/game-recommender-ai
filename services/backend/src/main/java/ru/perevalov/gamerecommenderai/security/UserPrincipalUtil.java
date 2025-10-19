package ru.perevalov.gamerecommenderai.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.security.model.CustomUserPrincipal;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Component
public class UserPrincipalUtil {

    /** Метод возвращает String стим id, если пользователь зарегистрирован или "GUEST", если нет
     */
    // TODO: Переделать в PCAI-81
    public Mono<String> getSteamIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Mono.just(authentication.getName());
    }

    // TODO: Переделать в PCAI-81
    public Mono<UserRole> getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        return Mono.just(principal.getUserRole());
    }
}
