package ru.perevalov.gamerecommenderai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest; // ПРАВИЛЬНЫЙ ИМПОРТ
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameRecommenderException.class)
    public ResponseEntity<ErrorResponse> handleGameRecommenderException(GameRecommenderException ex, ServerHttpRequest request) {
        ErrorType errorType = ex.getErrorType();
        log.error("GameRecommenderException: {}", ex.getMessage(), ex);

        String message = (ex.getParams() != null)
                ? String.format(errorType.getDescription(), ex.getParams())
                : errorType.getDescription();

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getPath().value(), // ИЗМЕНЕНО
                errorType,
                message);

        return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(WebClientResponseException ex, ServerHttpRequest request) {
        log.error("WebClientException: {} - {}", ex.getStatusCode(), ex.getMessage(), ex);

        ErrorType errorType;
        if (ex.getStatusCode().is5xxServerError()) {
            errorType = ErrorType.AI_SERVICE_UNAVAILABLE;
        } else {
            errorType = switch (ex.getStatusCode().value()) { // Используем value() для надежности
                case 401 -> ErrorType.AI_SERVICE_UNAUTHORIZED;
                case 429 -> ErrorType.AI_SERVICE_RATE_LIMIT;
                default -> ErrorType.AI_SERVICE_ERROR;
            };
        }

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getPath().value(), // ИЗМЕНЕНО
                errorType,
                errorType.getDescription()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, ServerHttpRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorType errorType = ErrorType.DEFAULT_INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getPath().value(), // ИЗМЕНЕНО
                errorType,
                ex.getMessage()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }
}