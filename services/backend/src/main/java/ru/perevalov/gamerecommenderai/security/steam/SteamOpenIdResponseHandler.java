package ru.perevalov.gamerecommenderai.security.steam;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.AuthService;
import ru.perevalov.gamerecommenderai.security.CookieService;
import ru.perevalov.gamerecommenderai.service.UserService;

/**
 * * Handler респонса от OpenID при аутентификации через Steam.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamOpenIdResponseHandler {
    private final SteamOpenIdService steamOpenIdService;
    private final UserService userService;
    private final AuthService authService;
    private final CookieService cookieService;

    /**
     * * Handler охватывает следующие шаги:
     * 1. Верифицируем ответ (Проверяем, что его отдал именно Steam).
     * 2. Достаем из ответа SteamId.
     * 3. Создаем юзера в системе, если его не было прежде.
     * 4. Добавляем Steam Id в JWT токен.
     *
     * @param openIdResponse - ответ после аутентификации Steam
     * @param request        - содержат Refresh токен, в которые вшивается Steam id
     * @return
     */
    public RefreshAccessTokenResponse handle(OpenIdResponse openIdResponse,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        steamOpenIdService.verifyResponse(openIdResponse);
        Long steamId = steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId());
        log.info("Received OpenID callback, steamId={}", steamId);

        User user = userService.createIfNotExists(steamId);

        Cookie[] cookies = request.getCookies();
        String refreshTokenFromCookies = cookieService.extractRefreshTokenFromCookies(cookies);

        if (refreshTokenFromCookies != null && !refreshTokenFromCookies.isBlank()) {
            log.info("Found refresh token from cookie. Link steamId {} with refresh token", user.getSteamId());
            return authService.linkSteamIdToToken(refreshTokenFromCookies, user.getSteamId(), response);
        } else {
            String refreshTokenFromHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (refreshTokenFromHeader == null || refreshTokenFromHeader.isBlank()) {
                log.error("Missing authorization header when trying to inject steam id in to the JWT token " +
                        "for user with id {}", user.getId());
                throw new GameRecommenderException(
                        "Missing authorization header. Expected JWT token.",
                        "MISSING_AUTHORIZATION_HEADER",
                        HttpStatus.UNAUTHORIZED.value());
            }
            return authService.linkSteamIdToToken(refreshTokenFromHeader, user.getSteamId(), response);
        }
    }
}