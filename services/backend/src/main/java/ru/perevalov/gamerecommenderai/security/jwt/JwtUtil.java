package ru.perevalov.gamerecommenderai.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Component
public class JwtUtil {

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

}
