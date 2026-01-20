package ru.perevalov.gamerecommenderai.security;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.jwt.TokenType;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final CookieService cookieService;

    @Value("${tokens.access-token.ttl-in-minutes}")
    private long accessTtl;
    @Value("${tokens.refresh-token.ttl-in-days}")
    private long refreshTtl;

    private Duration getAccessTtl() {
        return Duration.ofMinutes(accessTtl);
    }

    private Duration getRefreshTtl() {
        return Duration.ofDays(refreshTtl);
    }

    public Mono<PreAuthResponse> preAuthorize(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> {
            String sessionId = UUID.randomUUID().toString();

            String accessToken = jwtUtil.createAccessToken(sessionId, getAccessTtl(), UserRole.GUEST, null);
            String refreshToken = jwtUtil.createRefreshToken(sessionId, getRefreshTtl(), UserRole.GUEST, null);

            cookieService.insertRefreshTokenInCookie(refreshToken, exchange);

            return PreAuthResponse.builder()
                    .accessToken(accessToken)
                    .accessExpiresIn(getAccessTtl().toSeconds())
                    .role(UserRole.GUEST.getAuthority())
                    .sessionId(sessionId)
                    .steamId(null)
                    .build();
        });
    }

    public Mono<AccessTokenResponse> refreshAccessToken(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> {
            String refreshToken = cookieService.extractRefreshTokenFromCookies(exchange);
            DecodedJWT decoded = decodeAndValidateRefreshToken(refreshToken);

            Long steamId = decoded.getClaim(JwtClaimKey.STEAM_ID.getKey()).asLong();
            UserRole userRole = resolveRoleClaim(decoded.getClaim(JwtClaimKey.ROLE.getKey()));
            String sessionId = decoded.getSubject();

            String newAccessToken = jwtUtil.createAccessToken(sessionId, getAccessTtl(), userRole, steamId);
            String newRefreshToken = jwtUtil.createRefreshToken(sessionId, getRefreshTtl(), userRole, steamId);

            cookieService.insertRefreshTokenInCookie(newRefreshToken, exchange);

            return AccessTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .accessExpiresIn(getAccessTtl().toSeconds())
                    .build();
        });
    }

    public Mono<AccessTokenResponse> linkSteamIdToToken(String refreshToken,
                                                       Long steamId,
                                                       ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> {
            DecodedJWT decoded = decodeAndValidateRefreshToken(refreshToken);
            String sessionId = decoded.getSubject();

            log.info("Linking steamId={} to session {}", steamId, sessionId);

            String newAccessToken = jwtUtil.createAccessToken(sessionId, getAccessTtl(), UserRole.USER, steamId);
            String newRefreshToken = jwtUtil.createRefreshToken(sessionId, getRefreshTtl(), UserRole.USER, steamId);

            cookieService.insertRefreshTokenInCookie(newRefreshToken, exchange);

            return AccessTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .accessExpiresIn(getAccessTtl().toSeconds())
                    .build();
        });
    }

    public boolean isRefreshToken(String token) {
        try {
            DecodedJWT decoded = decodeAndValidateRefreshToken(token);
            return isTokenType(decoded, TokenType.REFRESH);
        } catch (Exception e) {
            return false;
        }
    }

    private DecodedJWT decodeAndValidateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Refresh token is null or blank during validation");
            throw new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID);
        }

        DecodedJWT decoded = jwtUtil.decodeRefreshToken(refreshToken);
        jwtUtil.validateTokenExpiration(decoded);

        if (!isTokenType(decoded, TokenType.REFRESH)) {
            throw new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID);
        }

        return decoded;
    }

    private boolean isTokenType(DecodedJWT decodedJWT, TokenType expected) {
        Claim tokenTypeClaim = decodedJWT.getClaim(JwtClaimKey.TOKEN_TYPE.getKey());
        String tokenType = tokenTypeClaim != null ? tokenTypeClaim.asString() : null;
        return expected.getValue().equals(tokenType);
    }

    private UserRole resolveRoleClaim(Claim roleClaim) {
        if (roleClaim == null || roleClaim.isNull()) {
            return UserRole.GUEST;
        }
        try {
            return UserRole.fromAuthority(roleClaim.asString());
        } catch (IllegalArgumentException ex) {
            throw new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID);
        }
    }
}
