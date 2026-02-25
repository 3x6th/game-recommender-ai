package ru.perevalov.gamerecommenderai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
    public ResponseEntity<ErrorResponse> handleGameRecommenderException(GameRecommenderException ex, WebRequest request) {
        ErrorType errorType = ex.getErrorType();

        log.error("GameRecommenderException: {}", ex.getMessage(), ex);

        String message = (ex.getParams() != null)
                ? String.format(errorType.getDescription(), ex.getParams())
                : errorType.getDescription();

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getDescription(false),
                errorType,
                message);

        return ResponseEntity.status(errorResponse.getStatus()).body(errorResponse);
    }

    /**
     * Handles WebClientResponseException from external API calls, mapping HTTP status codes to ErrorType.
     *
     * @param ex      the WebClientResponseException to handle
     * @param request the web request that led to the candida exception
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(WebClientResponseException ex, WebRequest request) {
        log.error("WebClientException: {} - {}", ex.getStatusCode(), ex.getMessage(), ex);

        ErrorType errorType;

        if (ex.getStatusCode().is5xxServerError()) {
            errorType = ErrorType.AI_SERVICE_UNAVAILABLE;
        } else {
            errorType = switch (ex.getStatusCode()) {
                case HttpStatus.UNAUTHORIZED -> ErrorType.AI_SERVICE_UNAUTHORIZED;
                case HttpStatus.TOO_MANY_REQUESTS -> ErrorType.AI_SERVICE_RATE_LIMIT;
                default -> ErrorType.AI_SERVICE_ERROR;
            };
        }

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getDescription(false),
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
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorType errorType = ErrorType.DEFAULT_INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                request.getDescription(false),
                errorType,
                ex.getMessage()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }
}