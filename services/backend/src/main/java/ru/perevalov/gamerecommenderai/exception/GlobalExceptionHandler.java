package ru.perevalov.gamerecommenderai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest; // ПРАВИЛЬНЫЙ ИМПОРТ
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Global exception handler for all controllers.
 * Provides centralized error handling by mapping exceptions to standardized HTTP responses using ErrorType and ErrorResponse.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Handles GameRecommenderException, converting it to a standardized HTTP error response.
     *
     * @param ex      the GameRecommenderException to handle
     * @param request the web request that led to the exception
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType
     */
    @ExceptionHandler(GameRecommenderException.class)
    public ResponseEntity<ErrorResponse> handleGameRecommenderException(GameRecommenderException ex, ServerHttpRequest request) {
        ErrorType errorType = ex.getErrorType();
        log.error("GameRecommenderException: {}", ex.getMessage(), ex);

        String message = (ex.getParams() != null)
                ? String.format(errorType.getDescription(), ex.getParams())
                : errorType.getDescription();

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getPath().value(),
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

    /**
     * Handles generic Exception for any unexpected errors, responding with a default internal server error.
     *
     * @param ex      the generic Exception to handle
     * @param request the web request that led to the exception
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType#DEFAULT_INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, ServerHttpRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorType errorType = ErrorType.DEFAULT_INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getPath().value(),
                errorType,
                ex.getMessage()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }
}