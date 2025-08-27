package ru.perevalov.gamerecommenderai.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.application.name}")
    private String issuer;

    public String createToken(String sessionId, Duration ttl, UserRole role) {
        Algorithm alg = Algorithm.HMAC256(jwtSecret);
        Instant now = Instant.now();
        Date expiresAt = Date.from(now.plus(ttl));
        return JWT.create()
                .withIssuer(issuer)
                .withSubject("SessionId:" + sessionId)
                .withClaim("role", role.getAuthority())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(expiresAt)
                .sign(alg);
    }

    public DecodedJWT decodeToken(String token) {
        Algorithm alg = Algorithm.HMAC256(jwtSecret);
        JWTVerifier verifier = JWT.require(alg).withIssuer(issuer).build();
        return verifier.verify(token);
    }
}
