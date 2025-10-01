package ru.perevalov.gamerecommenderai.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Enumeration of error types used in API responses.
 * Each error type provides a human-readable description
 * and an associated HTTP status code to represent the error condition.
 */
@Getter
public enum ErrorType {
    ACCESS_TOKEN_EXPIRED("Access token expired", HttpStatus.UNAUTHORIZED),
    AI_SERVICE_ERROR("Error accessing AI service", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_RATE_LIMIT("Rate limit exceeded for AI service. Please try later.", HttpStatus.TOO_MANY_REQUESTS),
    AI_SERVICE_UNAUTHORIZED("Authorization error in AI service. Please check your API key.", HttpStatus.UNAUTHORIZED),
    AI_SERVICE_UNAVAILABLE("AI service is temporarily unavailable. Please try later.", HttpStatus.INTERNAL_SERVER_ERROR),
    AUTH_REFRESH_TOKEN_INVALID("Refresh token invalid", HttpStatus.UNAUTHORIZED),
    DEFAULT_INTERNAL_SERVER_ERROR("An internal error occurred. Please try again later.", INTERNAL_SERVER_ERROR),
    GRPC_AI_ERROR("Error from AI service: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    GRPC_COMMUNICATION_ERROR("Error communicating with gRPC service with exception: %s", HttpStatus.INTERNAL_SERVER_ERROR),
    MISSING_AUTHORIZATION_HEADER("Missing authorization header. Expected JWT token.", HttpStatus.UNAUTHORIZED),
    OPENID_VALIDATION_FAILED_ENDPOINT("OpenID validation failed: opEndpoint '%s' differs from expected in openId" +
            " authorization flow through Steam.", HttpStatus.UNAUTHORIZED),
    OPENID_VALIDATION_FAILED_RESPONSE("OpenID validation failed: Steam returned invalid response. Endpoint: %s," +
            " body: %s", HttpStatus.UNAUTHORIZED),
    STEAM_API_FETCH_OWNED_GAMES_ERROR("Failed to fetch owned games from Steam API. steamId=%s", HttpStatus.SERVICE_UNAVAILABLE),
    STEAM_API_PLAYER_SUMMARY_ERROR("Failed to fetch player summary from Steam API. steamId=%s", HttpStatus.SERVICE_UNAVAILABLE),
    STEAM_APP_DETAILS_MAPPING_ERROR("Failed to map app details for appid %s.", HttpStatus.INTERNAL_SERVER_ERROR),
    STEAM_APP_DETAILS_NOT_FOUND("App details for appid %s were not found.", HttpStatus.NOT_FOUND),
    STEAM_DATA_IN_APP_DETAILS_NOT_FOUND("Data in appDetails for appid %s were not found.", HttpStatus.NOT_FOUND),
    STEAM_ID_EXTRACTION_FAILED("Steam ID extraction failed: Invalid claimedId format '%s'. Expected format like" +
            " https://steamcommunity.com/id/76561197973845818",
            HttpStatus.BAD_REQUEST
    ),
    USER_NOT_FOUND("User with steam id %s was not found in system.", HttpStatus.NOT_FOUND),
    FAILED_TO_BUILD_AI_CONTEXT("Failed to build full context for AI service", HttpStatus.INTERNAL_SERVER_ERROR),
    STEAM_JSON_PROCESSING_ERROR("Failed to parse JSON response.", HttpStatus.INTERNAL_SERVER_ERROR),
    STEAM_STORE_API_FETCH_APP_DETAILS_ERROR("Failed to fetch app details from Steam Store API with appIds=%s", HttpStatus.SERVICE_UNAVAILABLE);

    private final String description;
    private final HttpStatus status;

    ErrorType(String description, HttpStatus status) {
        this.description = description;
        this.status = status;
    }
}
