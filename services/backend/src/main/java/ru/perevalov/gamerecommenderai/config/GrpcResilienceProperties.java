package ru.perevalov.gamerecommenderai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Конфигурационные свойства для Resilience4j gRPC клиента.
 * Группирует все настройки circuit breaker, retry и time limiter.
 */
@Data
@Component
@ConfigurationProperties(prefix = "resilience4j")
public class GrpcResilienceProperties {

    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Retry retry = new Retry();
    private TimeLimiter timeLimiter = new TimeLimiter();

    @Data
    public static class CircuitBreaker {
        private Instances instances = new Instances();

        @Data
        public static class Instances {
            private GrpcClient grpcClient = new GrpcClient();
        }

        @Data
        public static class GrpcClient {
            private int slidingWindowSize = 10;
            private int minimumNumberOfCalls = 5;
            private int permittedNumberOfCallsInHalfOpenState = 3;
            private Duration waitDurationInOpenState = Duration.ofSeconds(30);
            private float failureRateThreshold = 50.0f;
        }
    }

    @Data
    public static class Retry {
        private Instances instances = new Instances();

        @Data
        public static class Instances {
            private GrpcClient grpcClient = new GrpcClient();
        }

        @Data
        public static class GrpcClient {
            private int maxAttempts = 3;
            private Duration waitDuration = Duration.ofSeconds(1);
        }
    }

    @Data
    public static class TimeLimiter {
        private Instances instances = new Instances();

        @Data
        public static class Instances {
            private GrpcClient grpcClient = new GrpcClient();
        }

        @Data
        public static class GrpcClient {
            private Duration timeoutDuration = Duration.ofSeconds(10);
        }
    }
}
