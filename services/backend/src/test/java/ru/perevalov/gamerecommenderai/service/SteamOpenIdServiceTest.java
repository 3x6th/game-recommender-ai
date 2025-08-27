package ru.perevalov.gamerecommenderai.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.steam.SteamOpenIdService;

@ExtendWith(MockitoExtension.class)
class SteamOpenIdServiceTest {

    @Mock
    private WebClient webClient;

    private SteamOpenIdService steamOpenIdService;

    @BeforeEach
    void setUp() {
        steamOpenIdService = new SteamOpenIdService(webClient);
    }

    @Test
    @DisplayName("Test verify correct open id response functionality")
    void givenValidOpenIdResponse_whenVerifyResponse_thenVerifySuccessAndDoNothing() {
        // given
        OpenIdResponse response = OpenIdResponse.builder()
                .ns("ns")
                .opEndpoint("http://fake-endpoint")
                .claimedId("claimed")
                .identity("id")
                .returnTo("return")
                .responseNonce("nonce")
                .assocHandle("assoc")
                .signed("signed")
                .sig("sig")
                .build();

        // then
        Assertions.assertThatThrownBy(() -> steamOpenIdService.verifyResponse(response))
                .isInstanceOf(GameRecommenderException.class);
    }

    @Test
    @DisplayName("Test verify invalid open id response functionality")
    void givenInvalidVerifyResponseWithDifferentOpEndpoint_whenVerifyResponse_thenThrowsException() {
        // given
        OpenIdResponse response = OpenIdResponse.builder()
                .ns("ns")
                .opEndpoint("http://fake-endpoint")
                .claimedId("claimed")
                .identity("id")
                .returnTo("return")
                .responseNonce("nonce")
                .assocHandle("assoc")
                .signed("signed")
                .sig("sig")
                .build();
        response.setOpEndpoint("https://steam.com/openid");

        // when then
        Assertions.assertThatThrownBy(() -> steamOpenIdService.verifyResponse(response))
                .isInstanceOf(GameRecommenderException.class);

    }

    @Test
    @DisplayName("Test verify invalid open id response functionality")
    void givenInvalidVerifyResponse_whenVerifyResponse_thenThrowsException() {
        // given
        OpenIdResponse response = OpenIdResponse.builder()
                .ns("ns")
                .opEndpoint("https://steamcommunity.com/openid/login")
                .claimedId("claimed")
                .identity("id")
                .returnTo("return")
                .responseNonce("nonce")
                .assocHandle("assoc")
                .signed("signed")
                .sig("sig")
                .build();
        response.setOpEndpoint("https://steam.com/openid");

        // when then
        Assertions.assertThatThrownBy(() -> steamOpenIdService.verifyResponse(response))
                .isInstanceOf(GameRecommenderException.class);

    }

    @Test
    @DisplayName("Test extraction steam id with correct steam id functionality")
    void givenCorrectClaimedId_whenExtractSteamIdFromClaimedId_thenReturnSteamId() {
        // given
        String correctClaimedIdExample = "https://steamcommunity.com/id/76561197973845818";

        // when
        Long l = steamOpenIdService.extractSteamIdFromClaimedId(correctClaimedIdExample);

        // then
        Assertions.assertThat(l).isNotNull();
        Assertions.assertThat(l).isNotZero();
    }

    @Test
    @DisplayName("Test extraction steam id with incorrect claimed id functionality")
    void givenIncorrectClaimedId_whenExtractSteamIdFromClaimedId_thenReturnSteamId() {
        // given
        String incorrectClaimedId = "https://steamcommunity.com/profiles/pivnoymonstr";
        // when then
        Assertions.assertThatThrownBy(() -> steamOpenIdService.extractSteamIdFromClaimedId(incorrectClaimedId))
                .isInstanceOf(RuntimeException.class);
    }
}