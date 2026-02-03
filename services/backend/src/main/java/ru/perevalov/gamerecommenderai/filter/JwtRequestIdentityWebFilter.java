package ru.perevalov.gamerecommenderai.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.jwt.TokenType;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Component
@Order(-100)
public class JwtRequestIdentityWebFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public JwtRequestIdentityWebFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
            return attachIdentity(exchange, RequestIdentity.anonymous(), chain);
        }

        String token = authHeader.substring(7);
        try {
            DecodedJWT decoded = jwtUtil.decodeAccessToken(token);
            jwtUtil.validateTokenExpiration(decoded);

            String tokenType = decoded.getClaim(JwtClaimKey.TOKEN_TYPE.getKey()).asString();
            if (!TokenType.ACCESS.getValue().equals(tokenType)) {
                return attachIdentity(exchange, RequestIdentity.anonymous(), chain);
            }

            String roleClaim = decoded.getClaim(JwtClaimKey.ROLE.getKey()).asString();
            UserRole role;
            try {
                role = StringUtils.hasText(roleClaim) ? UserRole.fromAuthority(roleClaim) : UserRole.GUEST;
            } catch (IllegalArgumentException ex) {
                role = UserRole.GUEST;
            }

            Long steamId = decoded.getClaim(JwtClaimKey.STEAM_ID.getKey()).asLong();
            String sessionId = decoded.getSubject();

            return attachIdentity(exchange, new RequestIdentity(sessionId, role, steamId), chain);
        } catch (Exception e) {
            log.debug("Failed to parse access token, proceeding as guest", e);
            return attachIdentity(exchange, RequestIdentity.anonymous(), chain);
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
}
