package ru.perevalov.gamerecommenderai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;

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
     * @param exchange the server web exchange for the current request
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType
     */
    @ExceptionHandler(GameRecommenderException.class)
    public ResponseEntity<ErrorResponse> handleGameRecommenderException(
            GameRecommenderException ex,
            ServerWebExchange exchange) {

        log.error("GameRecommenderException: {}", ex.getMessage(), ex);

        ErrorType errorType = ex.getErrorType();

        String message = (ex.getParams() != null)
                ? String.format(errorType.getDescription(), ex.getParams())
                : errorType.getDescription();

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                exchange.getRequest().getPath().value(),
                errorType,
                message
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }

    /**
     * Handles InvalidMetaException, converting it to a standardized HTTP error response.
     *
     * @param ex      the InvalidMetaException to handle
     * @param exchange the server web exchange for the current request
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     */
    @ExceptionHandler(InvalidMetaException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMetaException(
            InvalidMetaException ex,
            ServerWebExchange exchange) {

        log.error("InvalidMetaException: {}", ex.getMessage(), ex);

        ErrorType errorType = ErrorType.INVALID_MESSAGE_META;

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                exchange.getRequest().getPath().value(),
                errorType,
                ex.getMessage()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }

    /**
     * Handles WebClientResponseException from external API calls, mapping HTTP status codes to ErrorType.
     *
     * @param ex      the WebClientResponseException to handle
     * @param exchange the server web exchange for the current request
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(
            WebClientResponseException ex,
            ServerWebExchange exchange) {

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
                exchange.getRequest().getPath().value(),
                errorType,
                errorType.getDescription()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }

    /**
     * Handles generic Exception for any unexpected errors, responding with a default internal server error.
     *
     * @param ex      the generic Exception to handle
     * @param exchange the server web exchange for the current request
     * @return ResponseEntity with {@link ErrorResponse} containing error details
     * @see ErrorType#DEFAULT_INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            ServerWebExchange exchange) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorType errorType = ErrorType.DEFAULT_INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = ErrorResponse.build(
                errorType.getStatus().value(),
                exchange.getRequest().getPath().value(),
                errorType,
                ex.getMessage()
        );

        return ResponseEntity.status(errorType.getStatus()).body(errorResponse);
    }
}
