package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
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
import java.util.Optional;

import static ru.perevalov.gamerecommenderai.security.steam.SteamOpenIdResponseHandler.STATE_QUERY_PARAM;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${application.base.url}")
    private String applicationUrl;
    @Value("${application.frontend.url:http://localhost:5173}")
    private String frontendUrl;
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
    public Mono<ResponseEntity<Void>> redirectToSteamForAuthorization(ServerWebExchange exchange) {
        MultiValueMap<String, String> queryParams = queryParamMultiValueMapGenerator(exchange);

        URI uri = UriComponentsBuilder
                .fromUriString(steamOpenIdLoginUrl)
                .queryParams(queryParams)
                .build()
                .encode()
                .toUri();

        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build());
    }

    /**
     * Ловим callback от провайдера аутентификации, верифицируем ответ от Steam.
     * Привязывает SteamId к текущей сессии и редиректит на фронтенд.
     */
    @GetMapping("/steam/return")
    public Mono<ResponseEntity<Void>> handleSteamCallback(ServerWebExchange exchange) {
        ru.perevalov.gamerecommenderai.dto.OpenIdResponse openIdResponse = buildOpenIdResponse(exchange);
        return steamOpenIdResponseHandler
                .handleReactively(openIdResponse, exchange)
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, buildFrontendRedirectUrl())
                        .build());
    }

    private String buildFrontendRedirectUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "/";
        }
        return frontendUrl.endsWith("/") ? frontendUrl : frontendUrl + "/";
    }


    private MultiValueMap<String, String> queryParamMultiValueMapGenerator(ServerWebExchange exchange) {
        String externalBaseUrl = resolveExternalBaseUrl(exchange);
        String returnTo = externalBaseUrl + "/api/v1/auth/steam/return";
        Optional<String> stateToken = tokenService.createSteamAuthStateToken(exchange);
        if (stateToken.isPresent()) {
            returnTo = UriComponentsBuilder
                    .fromUriString(returnTo)
                    .queryParam(STATE_QUERY_PARAM, stateToken.get())
                    .build()
                    .toUriString();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        params.add(OpenIdParam.NS.getKey(), OpenIdValue.NS.getValue());
        params.add(OpenIdParam.MODE.getKey(), OpenIdMode.CHECK_ID_SETUP.getValue());
        params.add(OpenIdParam.CLAIMED_ID.getKey(), OpenIdValue.IDENTIFIER_SELECT.getValue());
        params.add(OpenIdParam.IDENTITY.getKey(), OpenIdValue.IDENTIFIER_SELECT.getValue());
        params.add(OpenIdParam.RETURN_TO.getKey(), returnTo);
        params.add(OpenIdParam.REALM.getKey(), externalBaseUrl);

        return params;
    }

    private String resolveExternalBaseUrl(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();

        String forwardedHost = firstForwardedValue(headers.getFirst("X-Forwarded-Host"));
        String host = StringUtils.hasText(forwardedHost)
                ? forwardedHost
                : firstForwardedValue(headers.getFirst(HttpHeaders.HOST));

        if (StringUtils.hasText(host)) {
            String forwardedProto = firstForwardedValue(headers.getFirst("X-Forwarded-Proto"));
            String scheme = StringUtils.hasText(forwardedProto) ? forwardedProto : request.getURI().getScheme();
            if (!StringUtils.hasText(scheme)) {
                scheme = "https";
            }
            return scheme + "://" + host;
        }

        if (applicationUrl == null || applicationUrl.isBlank()) {
            return "";
        }
        return applicationUrl.endsWith("/") ? applicationUrl.substring(0, applicationUrl.length() - 1) : applicationUrl;
    }

    private String firstForwardedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.split(",")[0].trim();
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
