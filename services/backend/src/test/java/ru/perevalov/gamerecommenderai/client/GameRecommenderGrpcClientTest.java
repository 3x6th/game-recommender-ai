package ru.perevalov.gamerecommenderai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.FullAiContextRequestProto;
import ru.perevalov.gamerecommenderai.grpc.ReactorGameRecommenderServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.client.props.AiGrpcClientProps;
import ru.perevalov.gamerecommenderai.constant.GrpcAiMetricsConstant;
import ru.perevalov.gamerecommenderai.mapper.GrpcErrorMapper;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

@ExtendWith(MockitoExtension.class)
class GameRecommenderGrpcClientTest {

    @Mock
    private GrpcMapper grpcMapper;

    @Mock
    private ReactorGameRecommenderServiceGrpc.ReactorGameRecommenderServiceStub grpcStub;

    private final GrpcErrorMapper grpcErrorMapper = new GrpcErrorMapper();
    private SimpleMeterRegistry meterRegistry;
    private AiGrpcClientProps aiGrpcClientProps;

    @BeforeEach
    void setUp() {
        when(grpcMapper.toProto(any(AiContextRequest.class)))
                .thenReturn(FullAiContextRequestProto.getDefaultInstance());
        when(grpcStub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(grpcStub);
        meterRegistry = new SimpleMeterRegistry();
        aiGrpcClientProps = new AiGrpcClientProps(1, 1, 0);
    }

    @Test
    void getGameRecommendations_whenUnavailable_thenRetriesExactlyOnce() {
        AtomicInteger attempts = new AtomicInteger();
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.error(new StatusRuntimeException(Status.UNAVAILABLE));
                    }
                    return Mono.just(successResponse());
                });

        GameRecommenderGrpcClient client = buildClient(CircuitBreaker.ofDefaults("retry-unavailable"));

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .assertNext(response -> assertThat(response.getSuccess()).isTrue())
                .verifyComplete();

        assertThat(attempts.get()).isEqualTo(2);
        verify(grpcStub, times(2)).recommendGames(any(FullAiContextRequestProto.class));
        assertThat(counterValue(GrpcAiMetricsConstant.AI_RETRY_TOTAL)).isEqualTo(1.0);
        assertThat(timerCount(GrpcAiMetricsConstant.AI_SERVICE_LATENCY, GrpcAiMetricsConstant.TAG_OUTCOME, GrpcAiMetricsConstant.OUTCOME_SUCCESS)).isEqualTo(1L);
    }

    @Test
    void getGameRecommendations_whenDeadlineExceeded_thenRetriesExactlyOnce() {
        AtomicInteger attempts = new AtomicInteger();
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.error(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                    }
                    return Mono.just(successResponse());
                });

        GameRecommenderGrpcClient client = buildClient(CircuitBreaker.ofDefaults("retry-deadline"));

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .assertNext(response -> assertThat(response.getSuccess()).isTrue())
                .verifyComplete();

        assertThat(attempts.get()).isEqualTo(2);
        verify(grpcStub, times(2)).recommendGames(any(FullAiContextRequestProto.class));
        assertThat(counterValue(GrpcAiMetricsConstant.AI_RETRY_TOTAL)).isEqualTo(1.0);
    }

    @Test
    void getGameRecommendations_whenStatusIsNotRetryable_thenNoRetry() {
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenReturn(Mono.error(new StatusRuntimeException(Status.INVALID_ARGUMENT)));

        GameRecommenderGrpcClient client = buildClient(CircuitBreaker.ofDefaults("no-retry"));

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectErrorSatisfies(throwable -> {
                    GameRecommenderException ex = assertGameRecommenderException(throwable);
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.GRPC_COMMUNICATION_ERROR);
                })
                .verify();

        verify(grpcStub, times(1)).recommendGames(any(FullAiContextRequestProto.class));
        assertThat(counterValue(
                GrpcAiMetricsConstant.AI_FAILURES_TOTAL,
                GrpcAiMetricsConstant.TAG_REASON,
                ErrorType.GRPC_COMMUNICATION_ERROR.name()
        )).isEqualTo(1.0);
        assertThat(timerCount(GrpcAiMetricsConstant.AI_SERVICE_LATENCY, GrpcAiMetricsConstant.TAG_OUTCOME, GrpcAiMetricsConstant.OUTCOME_ERROR)).isEqualTo(1L);
    }

    @Test
    void getGameRecommendations_whenInternal_thenNoRetryAndFallbackUnavailable() {
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenReturn(Mono.error(new StatusRuntimeException(Status.INTERNAL)));

        GameRecommenderGrpcClient client = buildClient(CircuitBreaker.ofDefaults("internal-no-retry"));

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectErrorSatisfies(throwable -> {
                    GameRecommenderException ex = assertGameRecommenderException(throwable);
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.AI_SERVICE_UNAVAILABLE);
                })
                .verify();

        verify(grpcStub, times(1)).recommendGames(any(FullAiContextRequestProto.class));
        assertThat(counterValue(GrpcAiMetricsConstant.AI_RETRY_TOTAL)).isEqualTo(0.0);
        assertThat(counterValue(
                GrpcAiMetricsConstant.AI_FAILURES_TOTAL,
                GrpcAiMetricsConstant.TAG_REASON,
                ErrorType.AI_SERVICE_UNAVAILABLE.name()
        )).isEqualTo(1.0);
    }

    @Test
    void getGameRecommendations_whenOpenThenHalfOpen_thenFailFastAndThenPermitConfiguredCalls()
            throws InterruptedException {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "open-half-open",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50.0f)
                        .waitDurationInOpenState(Duration.ofMillis(200))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build()
        );

        AtomicInteger grpcCalls = new AtomicInteger();
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenAnswer(invocation -> {
                    if (grpcCalls.incrementAndGet() <= 2) {
                        return Mono.error(new StatusRuntimeException(Status.INVALID_ARGUMENT));
                    }
                    return Mono.just(successResponse());
                });

        GameRecommenderGrpcClient client = buildClient(circuitBreaker);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectError(GameRecommenderException.class)
                .verify();
        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectError(GameRecommenderException.class)
                .verify();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectErrorSatisfies(throwable -> {
                    GameRecommenderException ex = assertGameRecommenderException(throwable);
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.AI_SERVICE_UNAVAILABLE);
                })
                .verify();
        assertThat(grpcCalls.get()).isEqualTo(2);
        assertThat(counterValue(GrpcAiMetricsConstant.AI_CB_OPEN_TOTAL)).isEqualTo(1.0);

        Thread.sleep(250);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .assertNext(response -> assertThat(response.getSuccess()).isTrue())
                .verifyComplete();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .assertNext(response -> assertThat(response.getSuccess()).isTrue())
                .verifyComplete();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(grpcCalls.get()).isEqualTo(4);
    }

    @Test
    void getGameRecommendations_whenMinimumCallsNotReached_thenCircuitStaysClosed() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "minimum-calls",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50.0f)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build()
        );

        AtomicInteger grpcCalls = new AtomicInteger();
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenAnswer(invocation -> {
                    grpcCalls.incrementAndGet();
                    return Mono.error(new StatusRuntimeException(Status.INVALID_ARGUMENT));
                });

        GameRecommenderGrpcClient client = buildClient(circuitBreaker);

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(client.getGameRecommendations(requestMono()))
                    .expectError(GameRecommenderException.class)
                    .verify();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(grpcCalls.get()).isEqualTo(3);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectError(GameRecommenderException.class)
                .verify();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(grpcCalls.get()).isEqualTo(4);

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectErrorSatisfies(throwable -> {
                    GameRecommenderException ex = assertGameRecommenderException(throwable);
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.AI_SERVICE_UNAVAILABLE);
                })
                .verify();
        assertThat(grpcCalls.get()).isEqualTo(4);
    }

    @Test
    void getGameRecommendations_whenUnexpectedFailure_thenReturnsGameRecommenderException() {
        when(grpcStub.recommendGames(any(FullAiContextRequestProto.class)))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        GameRecommenderGrpcClient client = buildClient(CircuitBreaker.ofDefaults("exception-contract"));

        StepVerifier.create(client.getGameRecommendations(requestMono()))
                .expectErrorSatisfies(throwable -> {
                    GameRecommenderException ex = assertGameRecommenderException(throwable);
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.GRPC_COMMUNICATION_ERROR);
                })
                .verify();
        assertThat(counterValue(
                GrpcAiMetricsConstant.AI_FAILURES_TOTAL,
                GrpcAiMetricsConstant.TAG_REASON,
                ErrorType.GRPC_COMMUNICATION_ERROR.name()
        )).isEqualTo(1.0);
    }

    private GameRecommenderGrpcClient buildClient(CircuitBreaker circuitBreaker) {
        GameRecommenderGrpcClient client = new GameRecommenderGrpcClient(
                grpcMapper,
                grpcErrorMapper,
                circuitBreaker,
                meterRegistry,
                aiGrpcClientProps
        );
        ReflectionTestUtils.setField(client, "gameRecommenderServiceStub", grpcStub);
        return client;
    }

    private Mono<AiContextRequest> requestMono() {
        return Mono.just(AiContextRequest.builder().userMessage("find me game").build());
    }

    private RecommendationResponse successResponse() {
        return RecommendationResponse.newBuilder().setSuccess(true).setMessage("ok").build();
    }

    private GameRecommenderException assertGameRecommenderException(Throwable throwable) {
        assertThat(throwable).isInstanceOf(GameRecommenderException.class);
        return (GameRecommenderException) throwable;
    }

    private double counterValue(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter != null ? counter.count() : 0.0d;
    }

    private long timerCount(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        return timer != null ? timer.count() : 0L;
    }
}
