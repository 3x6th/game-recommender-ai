package ru.perevalov.gamerecommenderai.controller;

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
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdMode;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdParam;
import ru.perevalov.gamerecommenderai.security.openid.OpenIdValue;
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
    private final TokenService tokenService;

    @PostMapping("/preAuthorize")
    public Mono<ResponseEntity<PreAuthResponse>> preAuthorize(ServerWebExchange exchange) {
        return tokenService
                .preAuthorize(exchange)
                .map(resp -> ResponseEntity.ok()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.getAccessToken())
                        .body(resp));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AccessTokenResponse>> refreshAccessToken(ServerWebExchange exchange) {
        return tokenService.refreshAccessToken(exchange)
                .map(ResponseEntity::ok);
    }

    /**
     * Метод, принимающий GET запрос для редиректа на страницу входа Steam. Насыщается необходимыми для
     * OpenId query-параметрами и отправляет запрос в аутентификационный провайдер.
     */
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
    @GetMapping("/steam/return")
    public Mono<ResponseEntity<AccessTokenResponse>> handleSteamCallback(ServerWebExchange exchange) {
        ru.perevalov.gamerecommenderai.dto.OpenIdResponse openIdResponse = buildOpenIdResponse(exchange);
        return steamOpenIdResponseHandler
                .handleReactively(openIdResponse, exchange)
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

    private ru.perevalov.gamerecommenderai.dto.OpenIdResponse buildOpenIdResponse(ServerWebExchange exchange) {
        return ru.perevalov.gamerecommenderai.dto.OpenIdResponse.builder()
                .ns(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.NS.getKey()))
                .mode(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.MODE.getKey()))
                .opEndpoint(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.OP_ENDPOINT.getKey()))
                .claimedId(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.CLAIMED_ID.getKey()))
                .identity(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.IDENTITY.getKey()))
                .returnTo(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.RETURN_TO.getKey()))
                .responseNonce(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.RESPONSE_NONCE.getKey()))
                .assocHandle(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.ASSOC_HANDLE.getKey()))
                .signed(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.SIGNED.getKey()))
                .sig(exchange.getRequest().getQueryParams().getFirst(OpenIdParam.SIG.getKey()))
                .build();
    }
}