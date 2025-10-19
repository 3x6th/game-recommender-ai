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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.OpenIdResponse;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdMode;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdValue;
import ru.perevalov.gamerecommenderai.security.steam.SteamOpenIdResponseHandler;

import java.net.URI;

/**
 * Старые MVC контроллеры были переименованы (добавлен суффикс Old в конец названия метода). Они все еще обращаются к старой
 * логике, но принять запросы через netty не могут. Новые еактивные методы работают, но обращаются к методам-заглушкам в
 * сервисных классах
 */
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
    private final TokenService tokenService;

    @Deprecated(forRemoval = true)
    @PostMapping("/preAuthorizeOld")
    public ResponseEntity<PreAuthResponse> preAuthorizeOld(HttpServletResponse response) {
        PreAuthResponse resp = tokenService.preAuthorize(response);
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.getAccessToken())
                .body(resp);
    }

    /** Старый preAuthorize метод был переименован в preAuthorizeOld. Новый preAuthorize работает реактивно. На данный
     * момент обращается к методу-заглушке preAuthorizeReactively() в TokenService
     */
    @PostMapping("/preAuthorize")
    public Mono<ResponseEntity<PreAuthResponse>> preAuthorize(ServerWebExchange exchange) {
        return tokenService
                .preAuthorizeReactively(exchange) //TODO: Заменить вызов метода заглушки на готовый реактивный метод
                .map(resp -> ResponseEntity.ok()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.getAccessToken())
                        .body(resp));
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/refreshOld")
    public ResponseEntity<AccessTokenResponse> refreshAccessTokenOld(HttpServletRequest request,
                                                                     HttpServletResponse response) {
        AccessTokenResponse accessTokenResponse = tokenService.refreshAccessToken(request, response);
        return ResponseEntity.ok(accessTokenResponse);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AccessTokenResponse>> refreshAccessToken(ServerWebExchange exchange) {
        return tokenService.refreshAccessTokenReactively(exchange) //TODO: Заменить вызов метода заглушки на готовый реактивный метод
                .map(ResponseEntity::ok);
    }

    /**
     * Метод, принимающий GET запрос для редиректа на страницу входа Steam. Насыщается необходимыми для
     * OpenId query-параметрами и отправляет запрос в аутентификационный провайдер.
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/steamOld")
    public ResponseEntity<Void> redirectToSteamForAuthorizationOld() {
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

    @GetMapping("/steam")
    public Mono<ResponseEntity<Void>> redirectToSteamForAuthorization() {
        MultiValueMap<String, String> queryParams = queryParamMultiValueMapGenerator();

        URI uri = UriComponentsBuilder
                .fromUriString(steamOpenIdLoginUrl)
                .queryParams(queryParams)
                .build(true)
                .toUri();

        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build());
    }

    /**
     * Ловим callback от провайдера аутентификации, верифицируем ответ от Steam.
     * Возвращает новые Refresh и Access токены в случае привязки пользователя по Steam Id
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/steam/returnOld")
    public ResponseEntity<AccessTokenResponse> handleSteamCallbackOld(OpenIdResponse openIdResponse,
                                                                   HttpServletRequest request,
                                                                   HttpServletResponse response) {
        AccessTokenResponse handledResponse = steamOpenIdResponseHandler.handle(openIdResponse, request, response);
        return ResponseEntity.ok(handledResponse);
    }

    @GetMapping("/steam/return")
    public Mono<ResponseEntity<AccessTokenResponse>> handleSteamCallback(OpenIdResponse openIdResponse,
                                                                         ServerWebExchange exchange) {
        return steamOpenIdResponseHandler
                .handleReactively(openIdResponse, exchange) //TODO: Заменить вызов метода заглушки на готовый реактивный метод
                .map(ResponseEntity::ok);
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