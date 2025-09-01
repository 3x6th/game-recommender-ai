package ru.perevalov.gamerecommenderai.security.openid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Содержит параметр протокола OpenID, который указывает поставщику услуг каким образом
 * предполагается использовать OpenID для аутентификации и каких действий ждать
 * от OpenID-провайдера
 */

@Getter
@AllArgsConstructor
public enum OpenIdMode {
    CHECK_ID_SETUP("checkid_setup"),
    CHECK_AUTHENTICATION("check_authentication");

    private final String value;
}
