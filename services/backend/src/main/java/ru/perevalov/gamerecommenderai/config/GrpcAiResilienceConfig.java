package ru.perevalov.gamerecommenderai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcAiResilienceConfig {

    @Bean
    public CircuitBreaker grpcAiCircuitBreaker(GrpcAiCircuitBreakerProps props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(props.slidingWindowSize())
                .minimumNumberOfCalls(props.minimumNumberOfCalls())
                .failureRateThreshold(props.failureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(props.waitDurationOpenSeconds()))
                .permittedNumberOfCallsInHalfOpenState(props.permittedCallsInHalfOpen())
                .slowCallDurationThreshold(Duration.ofSeconds(props.slowCallDurationThreshold()))
                .slowCallRateThreshold(props.slowCallRateThreshold())
                .build();

        return CircuitBreaker.of(props.name(), config);
    }
}
