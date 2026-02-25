package ru.perevalov.gamerecommenderai.client.props;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai.service")
@Validated
public record AiServiceProps(
        @NotBlank String scheme,
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String recommendPath,
        @Min(1) @Max(1) int retryAttempts,
        @Min(1) long retryDelaySeconds,
        @Min(1) int connectTimeoutMillis,
        @Min(20) @Max(25) long responseTimeoutSeconds,
        @Min(1024) int maxInMemorySize) {
}
