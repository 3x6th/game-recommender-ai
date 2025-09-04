package ru.perevalov.gamerecommenderai.utils;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.security.model.CustomUserPrincipal;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public class DataUtils {
    public static User getUserTransient(Long steamId) {
        return new User(steamId, UserRole.USER);
    }

    public static User getUserPersisted(Long steamId) {
        User user = new User(steamId, UserRole.USER);
        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
    public static Long getMockSteamId(){
        return 76561197973845818L;
    }

    public static RequestPostProcessor securityMockMvcRequestPostProcessorsWithMockUser() {
        CustomUserPrincipal customUserPrincipal = new CustomUserPrincipal(DataUtils.getUserPersisted(getMockSteamId()));
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                customUserPrincipal, null, customUserPrincipal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authenticationToken);
    }
}