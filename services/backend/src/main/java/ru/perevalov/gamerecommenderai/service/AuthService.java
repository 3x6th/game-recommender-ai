package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Сервис управления аутентификацией.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StandardJwtService jwtService;

    /**
     * Генерирует и возвращает пользователю короткоживущий JWT-токен.
     *
     * @return JWT-токен.
     */
    public String preAuthorize() {
        String sessionId = UUID.randomUUID().toString();
        return jwtService.generateGuestToken(sessionId);
    }

}
