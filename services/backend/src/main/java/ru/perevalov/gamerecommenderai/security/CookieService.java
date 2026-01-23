package ru.perevalov.gamerecommenderai.security;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Service
public class CookieService {
    @Value("${application.security.cookie.refresh-token.name}")
    private String refreshTokenCookieName;
    @Value("${application.security.cookie.max-age-in-days}")
    private int cookieMaxAgeInDays;
    @Value("${application.security.cookie.secure:false}")
    private boolean secureCookie;

    /**
     * RefreshToken сохраняется в HttpOnly cookies, что защищает токен от компрометации через XSS атаку.
     */
    public void insertRefreshTokenInCookie(String refreshToken, ServerWebExchange exchange) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenCookieName, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(cookieMaxAgeInDays))
                .build();

        exchange.getResponse().getHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String extractRefreshTokenFromCookies(ServerWebExchange exchange) {
        if (exchange.getRequest().getCookies().getFirst(refreshTokenCookieName) == null) {
            return null;
        }
        return exchange.getRequest().getCookies().getFirst(refreshTokenCookieName).getValue();
    }
}
