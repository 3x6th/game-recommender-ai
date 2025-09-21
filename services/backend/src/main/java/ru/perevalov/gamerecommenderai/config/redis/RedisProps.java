package ru.perevalov.gamerecommenderai.config.redis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "redis")
@Validated
public record RedisProps(
        @NotBlank String redisUri,
        @Min(1) int capacity,
        @Min(1) int tokensPerRefill,
        @NotNull Duration refillDuration,
        @NotBlank String bucketKey
) {
}
