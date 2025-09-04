package ru.perevalov.gamerecommenderai.security.model;

import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.perevalov.gamerecommenderai.entity.User;

import java.util.Collection;
import java.util.Collections;

@AllArgsConstructor
public class CustomUserPrincipal implements UserDetails {
    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getAuthority()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.getSteamId() != null ? user.getSteamId().toString() : UserRole.GUEST.toString();
    }
}
