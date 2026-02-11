package ru.perevalov.gamerecommenderai.client.retry;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Slf4j
@Component
public class ReactiveRetryStrategy {

    public RetryBackoffSpec doBackoffRetry(
            int retryAttempts,
            long retryDelaySeconds,
            double jitter) {
        return Retry.backoff(
                            retryAttempts,
                            Duration.ofSeconds(retryDelaySeconds)
                    )
                    .jitter(jitter)
                    .doBeforeRetry(r -> log.warn(
                            "Starting retry attempt {} due to: {}",
                            r.totalRetries(),
                            r.failure().getMessage()
                    ))
                    .doAfterRetry(rs -> log.debug(
                            "Retry attempt finished, total attempts: {}",
                            rs.totalRetries()
                    ))
                    .filter(this::shouldRetry);

    }

    public RetryBackoffSpec doFixedDelayRetry(int retryAttempts, long retryDelaySeconds){
        return Retry.fixedDelay(retryAttempts, Duration.ofSeconds(retryDelaySeconds));
    }

    /**
     * Determines whether a failed request should be retried.
     *
     * @param throwable
     *         the exception from the failed request
     *
     * @return true if the request should be retried
     */
    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }

}
