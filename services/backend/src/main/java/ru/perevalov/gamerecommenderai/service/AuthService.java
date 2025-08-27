package ru.perevalov.gamerecommenderai.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.RefreshTokenRepository;
import ru.perevalov.gamerecommenderai.security.JwtUtil;
import ru.perevalov.gamerecommenderai.security.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${tokens.access-token.ttl-in-minutes}")
    private long accessTtl;
    @Value("${tokens.refresh-token.ttl-in-days}")
    private long refreshTtl;

    private Duration getAccessTtl()  {return Duration.ofMinutes(accessTtl);}
    private Duration getRefreshTtl() {return Duration.ofDays(refreshTtl)  ;}

    @Transactional
    public PreAuthResponse preAuthorize() {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.createToken(sessionId, getAccessTtl(), UserRole.GUEST);

        String refreshToken = jwtUtil.createToken(sessionId, getRefreshTtl(), UserRole.GUEST);
        RefreshToken entity = new RefreshToken();
        entity.setToken(refreshToken);
        entity.setSessionId(sessionId);
        refreshTokenRepository.save(entity);

        return PreAuthResponse.builder()
                .accessToken(accessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(getRefreshTtl().toSeconds())
                .role(UserRole.GUEST.getAuthority())
                .sessionId(sessionId)
                .build();
    }

    public RefreshAccessTokenResponse refresh(String inputRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenOrThrow(inputRefreshToken);

        if (refreshToken.getToken() == null || refreshToken.getToken().isBlank()) {
            throw new GameRecommenderException(
                    "Refresh token invalid",
                    "AUTH_REFRESH_TOKEN_INVALID",
                    HttpStatus.UNAUTHORIZED.value());
        }

        DecodedJWT decoded = jwtUtil.decodeToken(refreshToken.getToken());
        Instant expiresAt = decoded.getExpiresAtAsInstant();

        if (expiresAt.isBefore(Instant.now())) {
            throw new GameRecommenderException(
                    "Refresh token expired",
                    "AUTH_REFRESH_TOKEN_INVALID",
                    HttpStatus.UNAUTHORIZED.value());
        }

        String newAccessToken = jwtUtil.createToken(refreshToken.getSessionId(), getAccessTtl(), UserRole.GUEST);

        return RefreshAccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .build();
    }
}
