package ru.perevalov.gamerecommenderai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameRecommenderException.class)
    public ResponseEntity<ErrorResponse> handleGameRecommenderException(
            GameRecommenderException ex, WebRequest request) {

        log.error("GameRecommenderException: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(
            WebClientResponseException ex, WebRequest request) {

        log.error("WebClientException: {} - {}", ex.getStatusCode(), ex.getMessage(), ex);

        String errorCode = "AI_SERVICE_ERROR";
        String message = "Ошибка при обращении к AI сервису";

        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            errorCode = "AI_SERVICE_UNAUTHORIZED";
            message = "Ошибка авторизации в AI сервисе. Проверьте API ключ.";
        } else if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            errorCode = "AI_SERVICE_RATE_LIMIT";
            message = "Превышен лимит запросов к AI сервису. Попробуйте позже.";
        } else if (ex.getStatusCode().is5xxServerError()) {
            errorCode = "AI_SERVICE_UNAVAILABLE";
            message = "AI сервис временно недоступен. Попробуйте позже.";
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .message(message)
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Произошла внутренняя ошибка сервера")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
} 