package ru.perevalov.gamerecommenderai.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${security.jwt.secret}")
    private String jwtAccessSecret;

    @Value("${security.jwt.refresh-secret}")
    private String jwtRefreshSecret;

    @Value("${spring.application.name}")
    private String issuer;

    public String createAccessToken(String sessionId, Duration ttl, UserRole role, Long steamId) {
        return createToken(sessionId, ttl, role, steamId, TokenType.ACCESS, jwtAccessSecret);
    }

    public String createRefreshToken(String sessionId, Duration ttl, UserRole role, Long steamId) {
        return createToken(sessionId, ttl, role, steamId, TokenType.REFRESH, jwtRefreshSecret);
    }

    public String createSteamAuthStateToken(String sessionId, Duration ttl) {
        return createToken(sessionId, ttl, UserRole.GUEST, null, TokenType.STEAM_AUTH_STATE, jwtAccessSecret);
    }

    public DecodedJWT decodeSteamAuthStateToken(String token) {
        return decodeToken(token, jwtAccessSecret);
    }

    private String createToken(String sessionId,
                               Duration ttl,
                               UserRole role,
                               Long steamId,
                               TokenType tokenType,
                               String secret) {
        Algorithm alg = Algorithm.HMAC256(secret);
        Instant now = Instant.now();
        Date expiresAt = Date.from(now.plus(ttl));
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(sessionId)
                .withClaim(JwtClaimKey.STEAM_ID.getKey(), steamId)
                .withClaim(JwtClaimKey.ROLE.getKey(), role.getAuthority())
                .withClaim(JwtClaimKey.TOKEN_TYPE.getKey(), tokenType.getValue())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(expiresAt)
                .sign(alg);
    }

    public void validateTokenExpiration(DecodedJWT decodedJWT) {
        if (decodedJWT.getExpiresAtAsInstant().isBefore(Instant.now())) {
            throw new GameRecommenderException(ErrorType.ACCESS_TOKEN_EXPIRED);
        }
    }

    public DecodedJWT decodeAccessToken(String token) {
        return decodeToken(token, jwtAccessSecret);
    }

    public DecodedJWT decodeRefreshToken(String token) {
        return decodeToken(token, jwtRefreshSecret);
    }

    private DecodedJWT decodeToken(String token, String secret) {
        Algorithm alg = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(alg).withIssuer(issuer).build();
        return verifier.verify(token);
    }
}
