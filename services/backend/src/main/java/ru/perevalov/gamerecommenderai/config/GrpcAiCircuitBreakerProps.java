package ru.perevalov.gamerecommenderai.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "grpc.ai-circuit-breaker")
@Validated
public record GrpcAiCircuitBreakerProps(
        @NotBlank String name,
        @Min(1) int slidingWindowSize,
        @Min(1) int minimumNumberOfCalls,
        @DecimalMin("0.0") float failureRateThreshold,
        @Min(0) long waitDurationOpenSeconds,
        @Min(1) int permittedCallsInHalfOpen
) {
}

