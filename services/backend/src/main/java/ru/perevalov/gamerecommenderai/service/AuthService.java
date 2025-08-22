package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.entity.RefreshTokenEntity;
import ru.perevalov.gamerecommenderai.repository.RefreshTokenRepository;
import ru.perevalov.gamerecommenderai.security.JwtUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(3);

    public PreAuthResponse preAuthorize() {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.createGuestAccessToken(sessionId, ACCESS_TTL);
        String refreshToken = UUID.randomUUID().toString();

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setToken(refreshToken);
        entity.setSessionId(sessionId);
        entity.setExpiresAt(Instant.now().plus(REFRESH_TTL));
        refreshTokenRepository.save(entity);

        PreAuthResponse response = PreAuthResponse.builder()
                .accessToken(accessToken)
                .accessExpiresIn(ACCESS_TTL.toSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(REFRESH_TTL.toSeconds())
                .role("guest")
                .sessionId(sessionId)
                .build();

        return response;
    }

    public RefreshAccessTokenResponse refresh(String refreshToken) {
        if (refreshToken.isEmpty() ||
                refreshToken == null ||
                refreshTokenRepository.findByToken(refreshToken).isEmpty() ||
                refreshTokenRepository.findByToken(refreshToken).get().getExpiresAt().isAfter(Instant.now())
        ) {
            throw new RuntimeException("Invalid refresh token"); //TODO: Сделать Нормальный Эксепшн
        }
        RefreshTokenEntity refreshTokenEntity = refreshTokenRepository.findByToken(refreshToken).get();
        String newAccessToken = jwtUtil.createGuestAccessToken(refreshTokenEntity.getSessionId(), ACCESS_TTL);

        return RefreshAccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(ACCESS_TTL.toSeconds())
                .build();
    }
}
