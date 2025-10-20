package ru.perevalov.gamerecommenderai.security.steam;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.CookieService;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.service.SteamUserDataUpdater;
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
    private final TokenService tokenService;
    private final CookieService cookieService;
    private final SteamUserDataUpdater steamUserDataUpdater;

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
//    public AccessTokenResponse handle(OpenIdResponse openIdResponse,
//                                      HttpServletRequest request,
//                                      HttpServletResponse response) {
//        steamOpenIdService.verifyResponse(openIdResponse);
//        Long steamId = steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId());
//        log.info("Received OpenID callback, steamId={}", steamId);
//
//        User user = userService.createIfNotExists(steamId);
//
//        Cookie[] cookies = request.getCookies();
//        String refreshTokenFromCookies = cookieService.extractRefreshTokenFromCookies(cookies);
//
//        if (refreshTokenFromCookies != null && !refreshTokenFromCookies.isBlank()) {
//            log.info("Found refresh token from cookie. Link steamId {} with refresh token", user.getSteamId());
//            return tokenService.linkSteamIdToToken(refreshTokenFromCookies, user.getSteamId(), response);
//        } else {
//            String refreshTokenFromHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
//
//            if (refreshTokenFromHeader == null || refreshTokenFromHeader.isBlank()) {
//                log.error("Missing authorization header when trying to inject steam id in to the JWT token " +
//                        "for user with id {}", user.getId());
//                throw new GameRecommenderException(ErrorType.MISSING_AUTHORIZATION_HEADER);
//            }
//            return tokenService.linkSteamIdToToken(refreshTokenFromHeader, user.getSteamId(), response);
//        }
//    }
    public Mono<AccessTokenResponse> handle(OpenIdResponse openIdResponse,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        return steamOpenIdService.verifyResponse(openIdResponse)
                .then(steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId()))
                .flatMap(steamId -> {
                    log.info("Received OpenID callback, steamId={}", steamId);

                    return userService.createIfNotExists(steamId)
                            .flatMap(user ->
                                    steamUserDataUpdater.updateUserData(user.getSteamId())
                                            .then(linkTokens(user, request, response))
                            );
                });
    }

    private Mono<AccessTokenResponse> linkTokens(User user,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshTokenFromCookies = cookieService.extractRefreshTokenFromCookies(request.getCookies());

        if (refreshTokenFromCookies != null && !refreshTokenFromCookies.isBlank()) {
            log.info("Found refresh token from cookie. Link steamId {} with refresh token", user.getSteamId());
            return tokenService.linkSteamIdToToken(refreshTokenFromCookies, user.getSteamId(), response);
        }

        String refreshTokenFromHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (refreshTokenFromHeader == null || refreshTokenFromHeader.isBlank()) {
            log.error("Missing authorization header when trying to inject steam id in to the JWT token " +
                    "for user with id {}", user.getId());
            return Mono.error(new GameRecommenderException(ErrorType.MISSING_AUTHORIZATION_HEADER));
        }

        return tokenService.linkSteamIdToToken(refreshTokenFromHeader, user.getSteamId(), response);
    }
}