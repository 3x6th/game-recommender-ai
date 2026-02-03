package ru.perevalov.gamerecommenderai.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum JwtClaimKey {
    ROLE("role"),
    STEAM_ID("steamId"),
    TOKEN_TYPE("tokenType");

    private final String key;
}
