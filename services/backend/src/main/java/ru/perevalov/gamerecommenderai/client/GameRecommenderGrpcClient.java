package ru.perevalov.gamerecommenderai.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.client.props.AiGrpcClientProps;
import ru.perevalov.gamerecommenderai.constant.GrpcAiMetricsConstant;
import ru.perevalov.gamerecommenderai.grpc.ReactorGameRecommenderServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.mapper.GrpcErrorMapper;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

/**
 * gRPC client for AI service using Spring Boot Starters
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameRecommenderGrpcClient {

    private final GrpcMapper mapper;
    private final GrpcErrorMapper grpcErrorMapper;
    private final CircuitBreaker grpcAiCircuitBreaker;
    private final MeterRegistry meterRegistry;
    private final AiGrpcClientProps aiGrpcClientProps;

    @GrpcClient("ai-service")
    private ReactorGameRecommenderServiceGrpc.ReactorGameRecommenderServiceStub gameRecommenderServiceStub;

    /**
     * Реактивная версия получения game-recommendations
     */
    public Mono<RecommendationResponse> getGameRecommendations(Mono<AiContextRequest> aiContextRequest) {
        ReactorGameRecommenderServiceGrpc.ReactorGameRecommenderServiceStub stubWithDeadline =
                gameRecommenderServiceStub.withDeadlineAfter(
                        aiGrpcClientProps.deadlineSeconds(),
                        TimeUnit.SECONDS
                );

        return Mono.defer(() -> {
            Timer.Sample latencySample = Timer.start(meterRegistry);

            return aiContextRequest.doOnNext(req -> log.info(
                                           "Sending recommendation request: message={}",
                                           req.getUserMessage()
                                   ))
                    .map(mapper::toProto)
                    .flatMap(stubWithDeadline::recommendGames)
                    .retryWhen(Retry.fixedDelay(
                                    aiGrpcClientProps.retryMaxAttempts(),
                                    java.time.Duration.ofMillis(aiGrpcClientProps.retryBackoffMs())
                            )
                            .filter(grpcErrorMapper::isRetryableGrpcError)
                            .doBeforeRetry(r -> {
                                meterRegistry.counter(GrpcAiMetricsConstant.AI_RETRY_TOTAL).increment();
                                log.warn(
                                        "Retrying gRPC request after transient error, attempt {}",
                                        r.totalRetries() + 1
                                );
                            }))
                    .transformDeferred(CircuitBreakerOperator.of(grpcAiCircuitBreaker))
                    .onErrorMap(error -> {
                        if (grpcErrorMapper.isCircuitBreakerOpen(error)) {
                            meterRegistry.counter(GrpcAiMetricsConstant.AI_CB_OPEN_TOTAL).increment();
                        }
                        return grpcErrorMapper.mapGrpcError(error);
                    })
                    .doOnSuccess(response -> {
                        stopLatency(latencySample, GrpcAiMetricsConstant.OUTCOME_SUCCESS);
                        log.info("Received recommendation response: response={}", response.getSuccess());
                    })
                    .doOnError(error -> {
                        String failureReason = grpcErrorMapper.resolveFailureReason(error);
                        meterRegistry.counter(
                                GrpcAiMetricsConstant.AI_FAILURES_TOTAL,
                                GrpcAiMetricsConstant.TAG_REASON,
                                failureReason
                        ).increment();
                        stopLatency(latencySample, GrpcAiMetricsConstant.OUTCOME_ERROR);
                        log.error(
                                "Error getting recommendations from gRPC service, fallback_reason={}",
                                failureReason,
                                error
                        );
                    });
        });
    }

    private void stopLatency(Timer.Sample latencySample, String outcome) {
        latencySample.stop(meterRegistry.timer(
                GrpcAiMetricsConstant.AI_SERVICE_LATENCY,
                GrpcAiMetricsConstant.TAG_OUTCOME,
                outcome
        ));
    }

}
