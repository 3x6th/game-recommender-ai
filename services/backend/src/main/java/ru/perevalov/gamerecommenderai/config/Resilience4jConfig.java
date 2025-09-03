package ru.perevalov.gamerecommenderai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class Resilience4jConfig {

    private final GrpcResilienceProperties grpcResilienceProperties;

    @Value("${app.grpc.resilience.instance-name:grpcClient}")
    private String grpcInstanceName;

    @Bean
    public CircuitBreaker grpcCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(grpcResilienceProperties.getCircuitBreaker().getInstances().getGrpcClient().getSlidingWindowSize())
                .minimumNumberOfCalls(grpcResilienceProperties.getCircuitBreaker().getInstances().getGrpcClient().getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(grpcResilienceProperties.getCircuitBreaker().getInstances().getGrpcClient().getPermittedNumberOfCallsInHalfOpenState())
                .waitDurationInOpenState(grpcResilienceProperties.getCircuitBreaker().getInstances().getGrpcClient().getWaitDurationInOpenState())
                .failureRateThreshold(grpcResilienceProperties.getCircuitBreaker().getInstances().getGrpcClient().getFailureRateThreshold())
                .recordExceptions(Exception.class)
                .build();

        return CircuitBreaker.of(grpcInstanceName, config);
    }

    @Bean
    public Retry grpcRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(grpcResilienceProperties.getRetry().getInstances().getGrpcClient().getMaxAttempts())
                .waitDuration(grpcResilienceProperties.getRetry().getInstances().getGrpcClient().getWaitDuration())
                .retryExceptions(Exception.class)
                .build();

        return Retry.of(grpcInstanceName, config);
    }

    @Bean
    public TimeLimiter grpcTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(grpcResilienceProperties.getTimeLimiter().getInstances().getGrpcClient().getTimeoutDuration())
                .build();

        return TimeLimiter.of(grpcInstanceName, config);
    }
}
