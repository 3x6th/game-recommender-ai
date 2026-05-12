package ru.perevalov.gamerecommenderai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;
import ru.perevalov.gamerecommenderai.security.steam.SteamOpenIdResponseHandler;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private SteamOpenIdResponseHandler steamOpenIdResponseHandler;
    @Mock
    private TokenService tokenService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(steamOpenIdResponseHandler, tokenService);
        ReflectionTestUtils.setField(controller, "applicationUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(controller, "steamOpenIdLoginUrl", "https://steamcommunity.com/openid/login");
    }

    @Test
    void givenSteamAuthState_whenRedirectToSteam_thenEncodesNestedReturnToUrl() {
        String stateToken = "header.payload.signature==";
        when(tokenService.createSteamAuthStateToken(any())).thenReturn(Optional.of(stateToken));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("http://localhost:8080/api/v1/auth/steam")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.playcure.test")
                .build());

        ResponseEntity<Void> response = controller.redirectToSteamForAuthorization(exchange).block();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        Assertions.assertThat(location).isNotBlank();
        Assertions.assertThat(location).contains("openid.return_to=");
        Assertions.assertThat(location).doesNotContain("pc_steam_state=" + stateToken);

        String decodedLocation = URLDecoder.decode(location, StandardCharsets.UTF_8);
        Assertions.assertThat(decodedLocation)
                .contains("openid.return_to=https://api.playcure.test/api/v1/auth/steam/return"
                        + "?pc_steam_state=" + stateToken)
                .contains(OpenIdParam.REALM.getKey() + "=https://api.playcure.test");
    }
}
