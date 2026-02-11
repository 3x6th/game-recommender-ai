package ru.perevalov.gamerecommenderai.security.steam;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.CookieService;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.model.UserRole;
import ru.perevalov.gamerecommenderai.service.SteamUserDataService;
import ru.perevalov.gamerecommenderai.service.UserService;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SteamOpenIdResponseHandlerTest {

    @Mock
    private SteamOpenIdService steamOpenIdService;
    @Mock
    private UserService userService;
    @Mock
    private TokenService tokenService;
    @Mock
    private CookieService cookieService;
    @Mock
    private SteamUserDataService steamUserDataService;

    private MeterRegistry meterRegistry;
    private SteamOpenIdResponseHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new SteamOpenIdResponseHandler(
                steamOpenIdService,
                userService,
                tokenService,
                cookieService,
                steamUserDataService,
                meterRegistry
        );
    }

    @Test
    void givenValidOpenIdAndRefreshToken_whenHandle_thenReturnsTokensAndWritesMetrics() {
        // given
        OpenIdResponse openIdResponse = OpenIdResponse.builder()
                .claimedId("https://steamcommunity.com/id/76561198000000000")
                .build();

        long steamId = 76561198000000000L;

        User user = new User(steamId, UserRole.USER);
        user.setId(UUID.randomUUID());

        when(steamOpenIdService.verifyResponse(openIdResponse)).thenReturn(Mono.empty());
        when(steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId())).thenReturn(steamId);
        when(userService.createIfNotExists(steamId)).thenReturn(Mono.just(user));
        when(cookieService.extractRefreshTokenFromCookies(ArgumentMatchers.any())).thenReturn("refresh-token");

        AccessTokenResponse tokenResponse = AccessTokenResponse.builder()
                .accessToken("access-token")
                .accessExpiresIn(900)
                .build();
        when(tokenService.linkSteamIdToToken(eq("refresh-token"), eq(steamId), any())).thenReturn(Mono.just(tokenResponse));
        when(steamUserDataService.syncUserData(user)).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/steam/return").build());

        // when / then
        StepVerifier.create(handler.handleReactively(openIdResponse, exchange))
                .expectNextMatches(resp -> "access-token".equals(resp.getAccessToken()))
                .verifyComplete();

        Assertions.assertThat(meterRegistry.get("steam_auth_attempts").counter().count()).isEqualTo(1.0);
        Assertions.assertThat(meterRegistry.get("steam_auth_success").counter().count()).isEqualTo(1.0);

        verify(tokenService, times(1)).linkSteamIdToToken(eq("refresh-token"), eq(steamId), any());
        verify(steamUserDataService, times(1)).syncUserData(user);
    }

    @Test
    void givenMissingRefreshToken_whenHandle_thenFailsAndWritesFailureMetric() {
        // given
        OpenIdResponse openIdResponse = OpenIdResponse.builder()
                .claimedId("https://steamcommunity.com/id/76561198000000000")
                .build();

        long steamId = 76561198000000000L;
        User user = new User(steamId, UserRole.USER);
        user.setId(UUID.randomUUID());

        when(steamOpenIdService.verifyResponse(openIdResponse)).thenReturn(Mono.empty());
        when(steamOpenIdService.extractSteamIdFromClaimedId(openIdResponse.getClaimedId())).thenReturn(steamId);
        when(userService.createIfNotExists(steamId)).thenReturn(Mono.just(user));
        when(cookieService.extractRefreshTokenFromCookies(any())).thenReturn(null);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/steam/return").build());

        // when / then
        StepVerifier.create(handler.handleReactively(openIdResponse, exchange))
                .expectErrorSatisfies(err -> {
                    Assertions.assertThat(err).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) err;
                    Assertions.assertThat(gre.getErrorType()).isEqualTo(ErrorType.MISSING_AUTHORIZATION_HEADER);
                })
                .verify();

        Assertions.assertThat(meterRegistry.get("steam_auth_attempts").counter().count()).isEqualTo(1.0);
        Assertions.assertThat(meterRegistry
                .get("steam_auth_failure")
                .tag("reason", ErrorType.MISSING_AUTHORIZATION_HEADER.name())
                .counter()
                .count()).isEqualTo(1.0);

        verify(tokenService, never()).linkSteamIdToToken(any(), anyLong(), any());
        verify(steamUserDataService, never()).syncUserData(any());
    }
}

