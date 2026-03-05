package ru.perevalov.gamerecommenderai.security.steam;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
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
                    if (!StringUtils.hasText(refreshToken)) {
                        log.error("Missing refresh token for steam callback (steamId={})", steamId);
                        return Mono.error(new GameRecommenderException(ErrorType.MISSING_AUTHORIZATION_HEADER));
                    }

                    String sessionId = tokenService.extractSessionIdFromRefreshToken(refreshToken);

                    return tokenService.linkSteamIdToToken(refreshToken, steamId, exchange)
                            .flatMap(tokens -> bindGuestChats(sessionId, user.getId())
                                    .thenReturn(tokens))
                            .doOnNext(tokens ->
                                    Mono.defer(() -> steamUserDataService.syncUserData(user))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe(
                                                    unused -> { },
                                                    err -> log.error("User data sync failed for user={}", user.getId(), err)
                                            )
                            );
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

    private String resolveFailureReason(Throwable error) {
        if (error instanceof GameRecommenderException gre) {
            return gre.getErrorType().name();
        }
        return error != null ? error.getClass().getSimpleName() : "UNKNOWN";
    }
}
