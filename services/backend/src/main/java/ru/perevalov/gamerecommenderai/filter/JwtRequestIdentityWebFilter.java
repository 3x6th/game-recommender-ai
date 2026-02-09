package ru.perevalov.gamerecommenderai.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.exception.ErrorResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.jwt.TokenType;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Order(-100)
public class JwtRequestIdentityWebFilter implements WebFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtRequestIdentityWebFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (shouldSkip(exchange)) {
            return attachIdentity(exchange, RequestIdentity.anonymous(), chain);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader)) {
            return attachIdentity(exchange, RequestIdentity.anonymous(), chain);
        }

        if (!authHeader.startsWith("Bearer ")) {
            return reject(exchange, ErrorType.INVALID_AUTHORIZATION_HEADER, authHeader);
        }

        String token = authHeader.substring(7);
        if (!StringUtils.hasText(token)) {
            return reject(exchange, ErrorType.INVALID_AUTHORIZATION_HEADER, authHeader);
        }
        try {
            DecodedJWT decoded = jwtUtil.decodeAccessToken(token);
            jwtUtil.validateTokenExpiration(decoded);

            String tokenType = decoded.getClaim(JwtClaimKey.TOKEN_TYPE.getKey()).asString();
            if (!TokenType.ACCESS.getValue().equals(tokenType)) {
                return reject(exchange, ErrorType.ACCESS_TOKEN_INVALID);
            }

            String roleClaim = decoded.getClaim(JwtClaimKey.ROLE.getKey()).asString();
            UserRole role;
            try {
                role = StringUtils.hasText(roleClaim) ? UserRole.fromAuthority(roleClaim) : UserRole.GUEST;
            } catch (IllegalArgumentException ex) {
                return reject(exchange, ErrorType.ACCESS_TOKEN_INVALID);
            }

            Long steamId = decoded.getClaim(JwtClaimKey.STEAM_ID.getKey()).asLong();
            String sessionId = decoded.getSubject();

            return attachIdentity(exchange, new RequestIdentity(sessionId, role, steamId), chain);
        } catch (GameRecommenderException ex) {
            log.debug("Access token rejected: {}", ex.getMessage(), ex);
            return reject(exchange, ex.getErrorType(), ex.getParams());
        } catch (JWTVerificationException ex) {
            log.debug("Access token verification failed", ex);
            return reject(exchange, ErrorType.ACCESS_TOKEN_INVALID);
        } catch (Exception ex) {
            log.debug("Failed to parse access token", ex);
            return reject(exchange, ErrorType.ACCESS_TOKEN_INVALID);
        }
    }

    private Mono<Void> attachIdentity(ServerWebExchange exchange,
                                      RequestIdentity identity,
                                      WebFilterChain chain) {
        exchange.getAttributes().put(RequestIdentity.EXCHANGE_ATTRIBUTE, identity);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(RequestIdentity.class, identity));
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/actuator");
    }

    private Mono<Void> reject(ServerWebExchange exchange, ErrorType errorType, Object... params) {
        String message = (params != null && params.length > 0)
                ? String.format(errorType.getDescription(), params)
                : errorType.getDescription();
        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                exchange.getRequest().getPath().value(),
                errorType,
                message
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(errorType.getStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(bytes)));
    }
}
