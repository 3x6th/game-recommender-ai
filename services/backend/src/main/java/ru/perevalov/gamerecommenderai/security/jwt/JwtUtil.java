package ru.perevalov.gamerecommenderai.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component("jwtUtil")
public class JwtUtil {

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final static String ROLE_CLAIM = "role";

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.application.name}")
    private String issuer;

    public String createToken(String sessionId, Duration ttl, UserRole role, Long steamId) {
        Algorithm alg = Algorithm.HMAC256(jwtSecret);
        Instant now = Instant.now();
        Date expiresAt = Date.from(now.plus(ttl));
        return JWT.create()
                .withIssuer(issuer)
                .withSubject("SessionId:" + sessionId)
                .withClaim(JwtClaimKey.STEAM_ID.getKey(), steamId)
                .withClaim(JwtClaimKey.ROLE.getKey(), role.getAuthority())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(expiresAt)
                .sign(alg);
    }

    public void validateTokenExpiration(DecodedJWT decodedJWT) {
        if (decodedJWT.getExpiresAtAsInstant().isBefore(Instant.now())) {
            throw new GameRecommenderException(
                    "Access token expired",
                    "ACCESS_TOKEN_EXPIRED",
                    HttpStatus.UNAUTHORIZED.value()
            );
        }
    }

    public DecodedJWT decodeToken(String token) {
        Algorithm alg = Algorithm.HMAC256(jwtSecret);
        JWTVerifier verifier = JWT.require(alg).withIssuer(issuer).build();
        return verifier.verify(token);
    }

    public boolean jwtTokenIsValid(HttpServletRequest request) {
        try {
            String bearerToken = extractToken(request);
            decodeToken(bearerToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UserRole extractRole(HttpServletRequest request) {
        String bearerToken = extractToken(request);
        DecodedJWT jwt = decodeToken(bearerToken);
        return roleClaim(jwt);
    }

    public String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER_NAME);
        return authHeader.substring(BEARER_PREFIX.length());
    }

    public UserRole roleClaim(DecodedJWT jwt) {
        return UserRole.valueOf(
                jwt.getClaim(ROLE_CLAIM).asString());
    }
}
