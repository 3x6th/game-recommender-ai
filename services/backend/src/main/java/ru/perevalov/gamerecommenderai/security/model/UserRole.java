package ru.perevalov.gamerecommenderai.security.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserRole {
    GUEST("GUEST"),
    USER("USER");

    private final String authority;

    public static UserRole fromAuthority(String authority) {
        for (UserRole role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        throw new IllegalArgumentException("User role was not found. Unknown authority: " + authority);
    }
}
