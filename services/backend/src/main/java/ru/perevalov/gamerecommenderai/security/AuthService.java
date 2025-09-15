package ru.perevalov.gamerecommenderai.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.micrometer.core.annotation.Counted;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.RefreshTokenRepository;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
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

    @Transactional
    @Counted(value = "auth.preauthorize.calls", description = "Number of preauthorize calls")
    public PreAuthResponse preAuthorize(HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.createToken(sessionId, getAccessTtl(), UserRole.GUEST, null);

        String refreshToken = jwtUtil.createToken(sessionId, getRefreshTtl(), UserRole.GUEST, null);
        RefreshToken entity = new RefreshToken(refreshToken, sessionId);
        RefreshToken savedToken = refreshTokenRepository.save(entity);

        cookieService.insertRefreshTokenInCookie(savedToken.getToken(), response);

        return PreAuthResponse.builder()
                .accessToken(accessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(getRefreshTtl().toSeconds())
                .role(UserRole.GUEST.getAuthority())
                .sessionId(sessionId)
                .build();
    }

    public RefreshAccessTokenResponse refresh(String inputRefreshToken, HttpServletResponse response) {
        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenOrThrow(inputRefreshToken);
        DecodedJWT decoded = decodeAndValidate(storedRefreshToken);

        Long steamId = decoded.getClaim(JwtClaimKey.STEAM_ID.getKey()).asLong();
        UserRole userRole = UserRole.fromAuthority(decoded.getClaim(JwtClaimKey.ROLE.getKey()).asString());

        String newAccessToken = jwtUtil.createToken(
                storedRefreshToken.getSessionId(),
                getAccessTtl(),
                userRole,
                steamId);

        String newRefreshToken = jwtUtil.createToken(
                storedRefreshToken.getSessionId(),
                getRefreshTtl(),
                userRole,
                steamId);

        storedRefreshToken.setToken(newRefreshToken);
        refreshTokenRepository.save(storedRefreshToken);

        cookieService.insertRefreshTokenInCookie(newRefreshToken, response);

        return RefreshAccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .build();
    }

    /**
     * Обновляем токен, привязывая к нему steamId юзера.
     */
    public RefreshAccessTokenResponse linkSteamIdToToken(String inputRefreshToken,
                                                         Long steamId,
                                                         HttpServletResponse response) {
        RefreshToken stored = refreshTokenRepository.findByTokenOrThrow(inputRefreshToken);
        log.info("Привязываем steamId={} к сессии {}", steamId, stored.getSessionId());

        String newAccessToken = jwtUtil.createToken(
                stored.getSessionId(),
                getAccessTtl(),
                UserRole.USER,
                steamId);

        String newRefreshToken = jwtUtil.createToken(
                stored.getSessionId(),
                getRefreshTtl(),
                UserRole.USER,
                steamId
        );

        stored.setToken(newRefreshToken);
        refreshTokenRepository.save(stored);

        cookieService.insertRefreshTokenInCookie(newRefreshToken, response);

        return RefreshAccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .build();
    }

    private DecodedJWT decodeAndValidate(RefreshToken refreshToken) {
        if (refreshToken.getToken() == null || refreshToken.getToken().isBlank()) {
            throw new GameRecommenderException(
                    "Refresh token invalid",
                    "AUTH_REFRESH_TOKEN_INVALID",
                    HttpStatus.UNAUTHORIZED.value());
        }

        DecodedJWT decoded = jwtUtil.decodeToken(refreshToken.getToken());
        jwtUtil.validateTokenExpiration(decoded);
        return decoded;
    }
}