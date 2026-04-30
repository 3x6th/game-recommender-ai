package ru.perevalov.gamerecommenderai.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.jwt.TokenType;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private CookieService cookieService;
    @Mock
    private DecodedJWT decodedJWT;
    @Mock
    private Claim tokenTypeClaim;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(jwtUtil, cookieService);
        ReflectionTestUtils.setField(tokenService, "steamAuthStateTtl", "");
    }

    @Test
    void givenBlankSteamAuthStateTtl_whenCreateStateToken_thenUsesDefaultTtl() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/auth/steam")
                .build());

        when(cookieService.extractRefreshTokenFromCookies(exchange)).thenReturn("refresh-token");
        when(jwtUtil.decodeRefreshToken("refresh-token")).thenReturn(decodedJWT);
        when(decodedJWT.getClaim(JwtClaimKey.TOKEN_TYPE.getKey())).thenReturn(tokenTypeClaim);
        when(tokenTypeClaim.asString()).thenReturn(TokenType.REFRESH.getValue());
        when(decodedJWT.getSubject()).thenReturn("session-id");
        when(jwtUtil.createSteamAuthStateToken(eq("session-id"), eq(Duration.ofMinutes(10))))
                .thenReturn("state-token");

        Optional<String> result = tokenService.createSteamAuthStateToken(exchange);

        Assertions.assertThat(result).contains("state-token");
        verify(jwtUtil).createSteamAuthStateToken("session-id", Duration.ofMinutes(10));
    }
}
