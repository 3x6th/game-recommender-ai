package ru.perevalov.gamerecommenderai.client.props;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "grpc.ai-client")
@Validated
public record AiGrpcClientProps(
        @Min(0) int deadlineSeconds,
        @Min(0) int retryMaxAttempts,
        @Min(0) long retryBackoffMs
) {
}

