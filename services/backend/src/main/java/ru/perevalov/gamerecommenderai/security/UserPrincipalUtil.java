package ru.perevalov.gamerecommenderai.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalUtil {

    /** Метод возвращает String стим id, если пользователь зарегистрирован или "GUEST", если нет
     */
    public String getSteamIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
