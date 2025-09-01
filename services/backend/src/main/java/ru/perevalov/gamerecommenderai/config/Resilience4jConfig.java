package ru.perevalov.gamerecommenderai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    @Value("${resilience4j.circuitbreaker.instances.grpcClient.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${resilience4j.circuitbreaker.instances.grpcClient.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${resilience4j.circuitbreaker.instances.grpcClient.permitted-number-of-calls-in-half-open-state:3}")
    private int permittedNumberOfCallsInHalfOpenState;

    @Value("${resilience4j.circuitbreaker.instances.grpcClient.wait-duration-in-open-state:30s}")
    private Duration waitDurationInOpenState;

    @Value("${resilience4j.circuitbreaker.instances.grpcClient.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${resilience4j.retry.instances.grpcClient.max-attempts:3}")
    private int maxAttempts;

    @Value("${resilience4j.retry.instances.grpcClient.wait-duration:1s}")
    private Duration retryWaitDuration;

    @Value("${resilience4j.timelimiter.instances.grpcClient.timeout-duration:10s}")
    private Duration timeoutDuration;

    @Bean
    public CircuitBreaker grpcCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .waitDurationInOpenState(waitDurationInOpenState)
                .failureRateThreshold(failureRateThreshold)
                .recordExceptions(Exception.class)
                .build();

        return CircuitBreaker.of("grpcClient", config);
    }

    @Bean
    public Retry grpcRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(retryWaitDuration)
                .retryExceptions(Exception.class)
                .build();

        return Retry.of("grpcClient", config);
    }

    @Bean
    public TimeLimiter grpcTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(timeoutDuration)
                .build();

        return TimeLimiter.of("grpcClient", config);
    }
}
