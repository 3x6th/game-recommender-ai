package ru.perevalov.gamerecommenderai.security.openid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Стандартные значения спецификации OpenID 2.0.
 */
@AllArgsConstructor
@Getter
public enum OpenIdValue {
    NS("http://specs.openid.net/auth/2.0"),
    IDENTIFIER_SELECT("http://specs.openid.net/auth/2.0/identifier_select");
    private final String value;
}
