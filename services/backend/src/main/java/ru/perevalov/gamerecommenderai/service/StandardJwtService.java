package ru.perevalov.gamerecommenderai.service;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.UserRole;

@Component("jwtService")
@RequiredArgsConstructor
public class StandardJwtService implements JwtService {

    private final Algorithm hmac256Algorithm;
    private final JwtUtil jwtUtil;

    /**
     * Генерирует гостевой JWT-токен для анонимного пользователя.
     *
     * @param sessionId ID сессии пользователя, для которого будет сгенерирован токен
     * @return Сгенерированный JWT-токен
     */
    @Override
    public String generateGuestToken(String sessionId) {
        return jwtUtil.createGuestTokenBuilder(sessionId)
                .sign(hmac256Algorithm);
    }

    /**
     * Проверяет валидность JWT-токена.
     *
     * @param request объект {@link HttpServletRequest}
     * @return <code>true</code>, если токен валиден, <code>false</code>, если нет
     */
    @Override
    public boolean jwtTokenIsValid(HttpServletRequest request) {
        try {
            jwtUtil.decodeJwtToken(request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public UserRole extractRole(HttpServletRequest request) {
        DecodedJWT jwt = jwtUtil.decodeJwtToken(request);
        return jwtUtil.roleClaim(jwt);
    }

}
