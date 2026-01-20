package ru.perevalov.gamerecommenderai.security.steam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.CookieService;
import ru.perevalov.gamerecommenderai.security.TokenService;
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

    public Mono<AccessTokenResponse> handleReactively(OpenIdResponse openIdResponse, ServerWebExchange exchange) {
        return steamOpenIdService.verifyResponse(openIdResponse)
                .then(Mono.fromSupplier(() ->
                        steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId())))
                .flatMap(steamId -> userService.createIfNotExists(steamId).thenReturn(steamId))
                .flatMap(steamId -> {
                    log.info("Received OpenID callback, steamId={}", steamId);
                    String refreshToken = resolveRefreshToken(exchange);
                    if (!StringUtils.hasText(refreshToken)) {
                        log.error("Missing refresh token for steam callback (steamId={})", steamId);
                        return Mono.error(new GameRecommenderException(ErrorType.MISSING_AUTHORIZATION_HEADER));
                    }
                    return tokenService.linkSteamIdToToken(refreshToken, steamId, exchange);
                });
    }

    private String resolveRefreshToken(ServerWebExchange exchange) {
        String refreshTokenFromCookies = cookieService.extractRefreshTokenFromCookies(exchange);
        if (StringUtils.hasText(refreshTokenFromCookies)) {
            return refreshTokenFromCookies;
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}