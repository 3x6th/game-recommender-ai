package ru.perevalov.gamerecommenderai.security.jwt;

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
    private final JwtUtil jwtUtil;

    @Override
    public void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            DecodedJWT decodedJWT = jwtUtil.decodeToken(token);
            jwtUtil.validateTokenExpiration(decodedJWT);

            Claim claim = decodedJWT.getClaim(JwtClaimKey.STEAM_ID.getKey());
            Long extractedSteamId = (claim != null) ? claim.asLong() : null;

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

        filterChain.doFilter(request, response);
    }
}