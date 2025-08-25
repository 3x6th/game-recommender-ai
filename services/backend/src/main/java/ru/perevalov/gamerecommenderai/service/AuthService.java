package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
    private final Duration ACCESS_TTL = Duration.ofMinutes(accessTtl);
    private final Duration REFRESH_TTL = Duration.ofDays(refreshTtl);

    public PreAuthResponse preAuthorize() {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.createGuestAccessToken(sessionId, ACCESS_TTL);
        String refreshToken = UUID.randomUUID().toString();

        RefreshToken entity = new RefreshToken();
        entity.setToken(refreshToken);
        entity.setSessionId(sessionId);
        entity.setExpiresAt(Instant.now().plus(REFRESH_TTL));
        refreshTokenRepository.save(entity);

        return PreAuthResponse.builder()
                .accessToken(accessToken)
                .accessExpiresIn(ACCESS_TTL.toSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(REFRESH_TTL.toSeconds())
                .role(UserRole.GUEST.getAuthority())
                .sessionId(sessionId)
                .build();
    }

    public RefreshAccessTokenResponse refresh(String inputRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(inputRefreshToken).orElseThrow(() ->
                new GameRecommenderException(
                        "Refresh token not found",
                        "AUTH_REFRESH_TOKEN_INVALID",
                        HttpStatus.UNAUTHORIZED.value()));

        if (refreshToken.getToken() == null || refreshToken.getToken().isBlank()) {
            throw new GameRecommenderException(
                    "Refresh token invalid",
                    "AUTH_REFRESH_TOKEN_INVALID",
                    HttpStatus.UNAUTHORIZED.value());
        }
        String newAccessToken = jwtUtil.createGuestAccessToken(refreshToken.getSessionId(), ACCESS_TTL);

        return RefreshAccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(ACCESS_TTL.toSeconds())
                .build();
    }
}
