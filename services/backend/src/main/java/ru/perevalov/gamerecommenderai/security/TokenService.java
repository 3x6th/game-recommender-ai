package ru.perevalov.gamerecommenderai.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.AccessTokenResponse;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.entity.RefreshToken;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
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
public class TokenService {

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
    public PreAuthResponse preAuthorize(HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();

        String accessToken = jwtUtil.createToken(sessionId, getAccessTtl(), UserRole.GUEST, null);
        String refreshToken = jwtUtil.createToken(sessionId, getRefreshTtl(), UserRole.GUEST, null);

        RefreshToken token = new RefreshToken(refreshToken, sessionId);
        RefreshToken savedToken = refreshTokenRepository.save(token);

        cookieService.insertRefreshTokenInCookie(savedToken.getToken(), response);

        return PreAuthResponse.builder()
                .accessToken(accessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .role(UserRole.GUEST.getAuthority())
                .sessionId(sessionId)
                .build();
    }

    public AccessTokenResponse refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenFromCookies = cookieService.extractRefreshTokenFromCookies(request.getCookies());
        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenOrThrow(refreshTokenFromCookies);

        DecodedJWT decoded = decodeAndValidate(storedRefreshToken);

        Long steamId = decoded.getClaim(JwtClaimKey.STEAM_ID.getKey()).asLong();
        UserRole userRole = UserRole.fromAuthority(decoded.getClaim(JwtClaimKey.ROLE.getKey()).asString());

        String newAccessToken = jwtUtil.createToken(
                storedRefreshToken.getSessionId(),
                getAccessTtl(),
                userRole,
                steamId);

        cookieService.insertRefreshTokenInCookie(storedRefreshToken.getToken(), response);

        return AccessTokenResponse.builder()
                .accessToken(newAccessToken)
                .accessExpiresIn(getAccessTtl().toSeconds())
                .build();
    }

    /**
     * Обновляем токен, привязывая к нему steamId юзера.
     */
//    @Transactional
//    public AccessTokenResponse linkSteamIdToToken(String inputRefreshToken,
//                                                  Long steamId,
//                                                  HttpServletResponse response) {
//        RefreshToken stored = refreshTokenRepository.findByTokenOrThrow(inputRefreshToken);
//        log.info("Привязываем steamId={} к сессии {}", steamId, stored.getSessionId());
//
//        String newAccessToken = jwtUtil.createToken(
//                stored.getSessionId(),
//                getAccessTtl(),
//                UserRole.USER,
//                steamId);
//
//        String newRefreshToken = jwtUtil.createToken(
//                stored.getSessionId(),
//                getRefreshTtl(),
//                UserRole.USER,
//                steamId
//        );
//
//        stored.setToken(newRefreshToken);
//        refreshTokenRepository.save(stored);
//
//        cookieService.insertRefreshTokenInCookie(newRefreshToken, response);
//
//        return AccessTokenResponse.builder()
//                .accessToken(newAccessToken)
//                .accessExpiresIn(getAccessTtl().toSeconds())
//                .build();
//    }
    public Mono<AccessTokenResponse> linkSteamIdToToken(String inputRefreshToken,
                                                                Long steamId,
                                                                HttpServletResponse response){
        return Mono.fromCallable(()->{
            log.info("Привязываем steamId={} к сессии (заглушка, без обращения к БД)", steamId);

            // Заглушка вместо поиска refresh-токена в БД
            RefreshToken stored = new RefreshToken();
            stored.setSessionId(UUID.randomUUID().toString());
            stored.setToken(inputRefreshToken);

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
            // Заглушка вместо сохранения refresh-токена
            stored.setToken(newRefreshToken);
            // todo: реактивное сохранение в репозиторий+добавление трназакции
            cookieService.insertRefreshTokenInCookie(newRefreshToken, response);

            return AccessTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .accessExpiresIn(getAccessTtl().toSeconds())
                    .build();
        });
    }

    private DecodedJWT decodeAndValidate(RefreshToken refreshToken) {
        if (refreshToken.getToken() == null || refreshToken.getToken().isBlank()) {
            log.warn("Refresh token is null or blank during validation");
            throw new GameRecommenderException(ErrorType.AUTH_REFRESH_TOKEN_INVALID);
        }

        DecodedJWT decoded = jwtUtil.decodeToken(refreshToken.getToken());
        jwtUtil.validateTokenExpiration(decoded);
        return decoded;
    }

    public boolean isRefreshToken(String token) {
        try {
            refreshTokenRepository.findByTokenOrThrow(token);
            return true;
        } catch (GameRecommenderException e) {
            return false;
        }
    }
}