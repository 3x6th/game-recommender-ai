package ru.perevalov.gamerecommenderai.filter;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.security.TokenService;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.model.CustomUserPrincipal;
import ru.perevalov.gamerecommenderai.security.model.UserRole;
import ru.perevalov.gamerecommenderai.service.UserService;

import java.io.IOException;

/**
 * Фильтр на каждый запрос для проверки наличия в заголовке JWT токена с Steam ID для
 * сохранения текущего пользователя в SecurityContextHolder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {
    private final UserService userService;
    private final TokenService tokenService;
    private final JwtUtil jwtUtil;

    @Override
    public void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);

            if (!accessToken.isEmpty()) {
                if (isBearerTokenIsRefreshToken(accessToken)) {
                    throw new GameRecommenderException(ErrorType.INVALID_AUTHORIZATION_HEADER,
                            "Authorization header cannot be a refresh accessToken");
                }

                decodeJwtAndSetAuthentication(accessToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Метод декодирует JWT токен и извлекает необходимых данные для сохранения пользователя в SecurityContext
     */

    private void decodeJwtAndSetAuthentication(String accessToken) {
        DecodedJWT decodedJWT = jwtUtil.decodeToken(accessToken);

        try {
            jwtUtil.validateTokenExpiration(decodedJWT);
        } catch (GameRecommenderException e) {
            if (e.getErrorType() == ErrorType.ACCESS_TOKEN_EXPIRED) {
                log.debug("Access token expired for user: {}", decodedJWT.getSubject());
            }
            throw e;
        }

        Claim claim = decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey());
        Long extractedSteamId = claim != null ? claim.asLong() : null;

        User user;
        CustomUserPrincipal userPrincipal;

        if (extractedSteamId != null) {
            user = userService.findBySteamId(extractedSteamId);
            log.info("Authenticated user from jwt: steamId={}", extractedSteamId);
        } else {
            user = new User();
            user.setRole(UserRole.GUEST);
            log.info("Authenticated guest user via session JWT, sessionId={}", decodedJWT.getSubject());
        }

        userPrincipal = new CustomUserPrincipal(user);
        UsernamePasswordAuthenticationToken authenticationToken
                = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    /**
     * Проверка, что в заголовок не передан Refresh Token. В заголовке должен быть исключительно Access Token
     *
     * @param token - Token from header
     * @return true if Token is refresh token otherwise false
     */
    private boolean isBearerTokenIsRefreshToken(String token) {
        return tokenService.isRefreshToken(token);
    }
}