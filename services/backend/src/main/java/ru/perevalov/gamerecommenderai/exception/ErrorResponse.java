package ru.perevalov.gamerecommenderai.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized API error response for consistent error handling.
 * Provides error details like status code, path, message, and timestamp for clients and debugging.
 * Used in HTTP responses (e.g., 400, 500) mapped from exceptions via {@link ErrorType}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String path;
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;

    public static ErrorResponse build(int status, String path, ErrorType errorType, String message) {
        String errorCodeFormatted = errorType.name().replace("_", " ").toLowerCase();
        return ErrorResponse.builder()
                .status(status)
                .path(path)
                .errorCode(errorCodeFormatted)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}