package ru.perevalov.gamerecommenderai.security;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class CookieService {
    @Value("${application.security.cookie.refresh-token.name}")
    private String refreshTokenCookieName;
    @Value("${application.security.cookie.max-age-in-days}")
    private int cookieMaxAgeInDays;

    /**
     * RefreshToken сохраняется в HttpOnly Cookies, что защищает токен от компрометации через XSS атаку.
     */
    public void insertRefreshTokenInCookie(String refreshToken, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenCookieName, refreshToken)
                .httpOnly(true)
                .secure(false) // TODO: в продакшене в true флаг
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(cookieMaxAgeInDays))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String extractRefreshTokenFromCookies(Cookie[] cookies) {
        String refreshToken = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (refreshTokenCookieName.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        return refreshToken;
    }
}
