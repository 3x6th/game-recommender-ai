package ru.perevalov.gamerecommenderai.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenRequest;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdMode;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdValue;
import ru.perevalov.gamerecommenderai.security.AuthService;
import ru.perevalov.gamerecommenderai.security.steam.SteamOpenIdResponseHandler;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${application.base.url}")
    private String applicationUrl;
    @Value("${steam.openid.endpoint}")
    private String steamOpenIdLoginUrl;
    private final SteamOpenIdResponseHandler steamOpenIdResponseHandler;
    private final AuthService authService;

    @PostMapping("/preAuthorize")
    public ResponseEntity<PreAuthResponse> preAuthorize(HttpServletResponse response) {
        PreAuthResponse resp = authService.preAuthorize(response);
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.getAccessToken())
                .body(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshAccessTokenResponse> refreshAccessToken(@RequestBody RefreshAccessTokenRequest request,
                                                                         HttpServletResponse response) {
        RefreshAccessTokenResponse refreshAccessTokenResponse = authService.refresh(request.getRefreshToken(), response);
        return ResponseEntity.ok(refreshAccessTokenResponse);
    }

    /**
     * Метод, принимающий GET запрос для редиректа на страницу входа Steam. Насыщается необходимыми для
     * OpenId query-параметрами и отправляет запрос в аутентификационный провайдер.
     */
    @GetMapping("/steam")
    public ResponseEntity<Void> redirectToSteamForAuthorization() {
        MultiValueMap<String, String> queryParams = queryParamMultiValueMapGenerator();

        URI uri = UriComponentsBuilder
                .fromUriString(steamOpenIdLoginUrl)
                .queryParams(queryParams)
                .build(true)
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build();
    }

    /**
     * Ловим callback от провайдера аутентификации, верифицируем ответ от Steam.
     * Возвращает новые Refresh и Access токены в случае привязки пользователя по Steam Id
     */
    @GetMapping("/steam/return")
    public ResponseEntity<RefreshAccessTokenResponse> handleSteamCallback(OpenIdResponse openIdResponse,
                                                                          HttpServletRequest request,
                                                                          HttpServletResponse response) {
        RefreshAccessTokenResponse handledResponse = steamOpenIdResponseHandler.handle(openIdResponse, request, response);
        return ResponseEntity.ok(handledResponse);
    }


    private MultiValueMap<String, String> queryParamMultiValueMapGenerator() {
        String returnTo = applicationUrl + "/api/v1/auth/steam/return";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        params.add(OpenIdParam.NS.getKey(), OpenIdValue.NS.getValue());
        params.add(OpenIdParam.MODE.getKey(), OpenIdMode.CHECK_ID_SETUP.getValue());
        params.add(OpenIdParam.CLAIMED_ID.getKey(), OpenIdValue.IDENTIFIER_SELECT.getValue());
        params.add(OpenIdParam.IDENTITY.getKey(), OpenIdValue.IDENTIFIER_SELECT.getValue());
        params.add(OpenIdParam.RETURN_TO.getKey(), returnTo);
        params.add(OpenIdParam.REALM.getKey(), applicationUrl);

        return params;
    }
}