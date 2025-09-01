package ru.perevalov.gamerecommenderai.security.openid;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Константы ключей query-параметров для OpenID.
 */

@AllArgsConstructor
@Getter
public enum OpenIdParam {

    NS("openid.ns"),
    MODE("openid.mode"),
    OP_ENDPOINT("openid.op_endpoint"),
    CLAIMED_ID("openid.claimed_id"),
    IDENTITY("openid.identity"),
    RETURN_TO("openid.return_to"),
    RESPONSE_NONCE("openid.response_nonce"),
    ASSOC_HANDLE("openid.assoc_handle"),
    SIGNED("openid.signed"),
    SIG("openid.sig"),
    REALM("openid.realm");

    private final String key;
}
