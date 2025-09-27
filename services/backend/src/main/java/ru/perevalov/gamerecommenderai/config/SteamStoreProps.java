package ru.perevalov.gamerecommenderai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "steam.store")
@Validated
public record SteamStoreProps(
        @NotBlank String scheme,
        @NotBlank String host,
        @NotBlank String getAppDetailsPath,
        @Min(0) int retryAttempts,
        @Min(0) long retryDelaySeconds) {
}