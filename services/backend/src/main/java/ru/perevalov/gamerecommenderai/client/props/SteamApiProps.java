package ru.perevalov.gamerecommenderai.client.props;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "steam.api")
@Validated
public record SteamApiProps(
        @NotBlank String scheme,
        @NotBlank String host,
        @NotBlank String getAppListPath,
        @Min(0) int retryAttempts,
        @Min(0) long retryDelaySeconds,
        @Min(0) long durationOfMinutes,
        @DecimalMin(value = "0.0") double jitter,
        @Min(0) int connectTimeoutMillis,
        @Min(0) int maxInMemorySize) {
}