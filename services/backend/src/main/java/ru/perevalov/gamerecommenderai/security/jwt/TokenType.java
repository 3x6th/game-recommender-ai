package ru.perevalov.gamerecommenderai.security.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh"),
    STEAM_AUTH_STATE("steam_auth_state");

    private final String value;
}
