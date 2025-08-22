package ru.perevalov.gamerecommenderai.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.UserRole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final static String ROLE_CLAIM = "role";
    private final static String GUEST_SUBJECT_PREFIX = "SessionId: ";

    @Value("${spring.application.name}")
    private String ISSUER;

    @Value("${security.token.guest-access.expiration.minutes}")
    private long accessExpirationMinutes;

    private final JWTVerifier jwtVerifier;

    public JWTCreator.Builder createGuestTokenBuilder(String sessionId) {
        Instant issuedAt = Instant.now();

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(createSubject(sessionId))
                .withClaim(ROLE_CLAIM, UserRole.GUEST_USER.toString())
                .withIssuedAt(issuedAt)
                .withExpiresAt(calculateExpirationTime(issuedAt));
    }

    public DecodedJWT decodeJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER_NAME);
        String bearerToken = authHeader.substring(BEARER_PREFIX.length());
        return jwtVerifier.verify(bearerToken);
    }

    public UserRole roleClaim(DecodedJWT jwt) {
        return UserRole.valueOf(
                jwt.getClaim(ROLE_CLAIM).asString());
    }

    private Instant calculateExpirationTime(Instant issuedAt) {
        return issuedAt.plus(accessExpirationMinutes, ChronoUnit.MINUTES);
    }

    private String createSubject(String sessionId) {
        return GUEST_SUBJECT_PREFIX + sessionId;
    }

}
