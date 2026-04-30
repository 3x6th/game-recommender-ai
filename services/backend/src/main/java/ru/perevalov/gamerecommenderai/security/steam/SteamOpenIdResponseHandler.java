package ru.perevalov.gamerecommenderai.security.steam;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.CookieService;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.service.ChatsService;
import ru.perevalov.gamerecommenderai.service.SteamUserDataService;
import ru.perevalov.gamerecommenderai.service.UserService;

/**
 * Handler ответа OpenID при аутентификации через Steam.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamOpenIdResponseHandler {
    public static final String STATE_QUERY_PARAM = "pc_steam_state";

    private final SteamOpenIdService steamOpenIdService;
    private final UserService userService;
    private final TokenService tokenService;
    private final CookieService cookieService;
    private final SteamUserDataService steamUserDataService;
    private final ChatsService chatsService;
    private final MeterRegistry meterRegistry;

    /**
     * Обрабатывает Steam OpenID callback и возвращает токены доступа.
     *
     * @param openIdResponse ответ OpenID
     * @param exchange текущий WebExchange
     * @return access token response
     */
    public Mono<AccessTokenResponse> handleReactively(OpenIdResponse openIdResponse, ServerWebExchange exchange) {
        meterRegistry.counter("steam_auth_attempts").increment();

        return steamOpenIdService.verifyResponse(openIdResponse)
                .then(Mono.fromSupplier(() ->
                        steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId())))
                .flatMap(userService::createIfNotExists)
                .flatMap(user -> {
                    Long steamId = user.getSteamId();
                    log.info("Received OpenID callback, steamId={}, userId={}", steamId, user.getId());
                    String refreshToken = resolveRefreshToken(exchange);
                    String stateSessionId = resolveSessionIdFromState(openIdResponse, exchange);

                    if (StringUtils.hasText(refreshToken)) {
                        String sessionId = tokenService.extractSessionIdFromRefreshToken(refreshToken);

                        return tokenService.linkSteamIdToToken(refreshToken, steamId, exchange)
                                .flatMap(tokens -> bindGuestChats(sessionId, user.getId())
                                        .thenReturn(tokens))
                                .doOnNext(tokens -> syncUserData(user));
                    }

                    String sessionId = stateSessionId;
                    if (!StringUtils.hasText(sessionId)) {
                        sessionId = UUID.randomUUID().toString();
                        log.warn(
                                "Missing refresh token and Steam auth state for callback; creating new user session "
                                        + "steamId={} sessionId={}",
                                steamId, sessionId);
                    } else {
                        log.info("Restored Steam callback session from signed state sessionId={}", sessionId);
                    }

                    String callbackSessionId = sessionId;
                    return tokenService.issueUserTokens(callbackSessionId, steamId, exchange)
                            .flatMap(tokens -> bindGuestChats(callbackSessionId, user.getId()).thenReturn(tokens))
                            .doOnNext(tokens -> syncUserData(user));
                })
                .doOnSuccess(resp -> meterRegistry.counter("steam_auth_success").increment())
                .doOnError(err -> meterRegistry
                        .counter("steam_auth_failure", "reason", resolveFailureReason(err))
                        .increment());
    }

    private Mono<Void> bindGuestChats(String sessionId, UUID userId) {
        if (!StringUtils.hasText(sessionId)) {
            log.warn("Skip bind guest chats: sessionId is empty for userId={}", userId);
            return Mono.empty();
        }
        return chatsService.bindGuestChatsToUser(sessionId, userId)
                .doOnNext(count -> log.info(
                        "Bound guest chats to userId={} sessionId={} rowsUpdated={}",
                        userId, sessionId, count))
                .onErrorResume(ex -> {
                    log.error("Failed to bind guest chats for userId={} sessionId={}", userId, sessionId, ex);
                    return Mono.empty();
                })
                .then();
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

    private String resolveSessionIdFromState(OpenIdResponse openIdResponse, ServerWebExchange exchange) {
        String stateToken = exchange.getRequest().getQueryParams().getFirst(STATE_QUERY_PARAM);
        if (!StringUtils.hasText(stateToken)) {
            stateToken = extractStateFromReturnTo(openIdResponse.getReturnTo());
        }
        if (!StringUtils.hasText(stateToken)) {
            return null;
        }
        return tokenService.extractSessionIdFromSteamAuthStateToken(stateToken).orElse(null);
    }

    private String extractStateFromReturnTo(String returnTo) {
        if (!StringUtils.hasText(returnTo)) {
            return null;
        }

        try {
            return UriComponentsBuilder
                    .fromUri(URI.create(returnTo))
                    .build(true)
                    .getQueryParams()
                    .getFirst(STATE_QUERY_PARAM);
        } catch (Exception ex) {
            log.warn("Failed to parse Steam OpenID return_to while extracting state", ex);
            return null;
        }
    }

    private void syncUserData(User user) {
        Mono.defer(() -> steamUserDataService.syncUserData(user))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> { },
                        err -> log.error("User data sync failed for user={}", user.getId(), err)
                );
    }

    private String resolveFailureReason(Throwable error) {
        if (error instanceof GameRecommenderException gre) {
            return gre.getErrorType().name();
        }
        return error != null ? error.getClass().getSimpleName() : "UNKNOWN";
    }
}
