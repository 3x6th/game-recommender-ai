package ru.perevalov.gamerecommenderai.security;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CookieService {
    @Value("${application.security.cookie.refresh-token.name}")
    private String refreshTokenCookieName;
    @Value("${application.security.cookie.max-age-in-seconds}")
    private int cookieMaxAge;

    public void insertRefreshTokenInCookie(String refreshToken, HttpServletResponse response) {
        Cookie cookie = new Cookie(refreshTokenCookieName, refreshToken);
        cookie.setHttpOnly(true);       // запрещаем получать доступ к куке JavaScript-скриптам браузера
        cookie.setPath("/");            // доступна во всём приложении
        cookie.setMaxAge(cookieMaxAge);
        log.debug("Creating session cookies '{}' (maxAge={}s)", refreshTokenCookieName, cookieMaxAge);

        response.addCookie(cookie);
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
