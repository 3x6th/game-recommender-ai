package ru.perevalov.gamerecommenderai.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.perevalov.gamerecommenderai.security.UserRole;

/**
 * Сервис отвечает за работу с JWT-токенами.
 */
public interface JwtService {

    /**
     * Генерирует гостевой JWT-токен для анонимного пользователя.
     *
     * @param sessionId ID сессии пользователя, для которого будет сгенерирован токен
     * @return Сгенерированный JWT-токен
     */
    String generateGuestToken(String sessionId);

    /**
     * Проверяет валидность JWT-токена.
     *
     * @param request объект {@link HttpServletRequest}
     * @return <code>true</code>, если токен валиден, <code>false</code>, если нет
     */
    boolean jwtTokenIsValid(HttpServletRequest request);

    /**
     * Декодирует JWT-токен и возвращает из него роль пользователя.
     *
     * @param request request объект {@link HttpServletRequest}
     * @return роль пользователя из JWT-токена
     */
    UserRole extractRole(HttpServletRequest request);

}
