package ru.perevalov.gamerecommenderai.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserRole {
    GUEST("Guest"),
    AUTHORIZED("Authorized user");

    private final String authority;
}
