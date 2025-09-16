package ru.perevalov.gamerecommenderai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "steam")
@Validated
public record SteamProps(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String getPlayerSummariesPath,
        @NotBlank String getOwnedGamesPath,
        @Min(0) int retryAttempts,
        @Min(0) long retryDelaySeconds) {
}