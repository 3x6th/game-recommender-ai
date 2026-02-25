package ru.perevalov.gamerecommenderai.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.client.props.AiServiceProps;
import ru.perevalov.gamerecommenderai.client.retry.ReactiveRetryStrategy;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.filter.RequestIdWebFilter;



class AiServiceClientWireMockIT {

    private static final String RECOMMEND_PATH = "/ai/v1/recommend";

    private WireMockServer wireMockServer;
    private AiContextRequest request;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        wireMockServer.resetAll();

        request = AiContextRequest.builder()
                .userMessage("recommend me something")
                .selectedTags(new String[]{"rpg"})
                .build();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void retryShouldHappenFor503_andReturnUnavailable() {
        AiServiceClient client = createClient(1, 0, 1, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .willReturn(WireMock.aResponse().withStatus(503).withBody("service unavailable")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectErrorSatisfies(error -> {
                    GameRecommenderException ex = Assertions.assertInstanceOf(GameRecommenderException.class, error);
                    Assertions.assertEquals(ErrorType.AI_SERVICE_UNAVAILABLE, ex.getErrorType());
                })
                .verify();

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void retryShouldHappenFor502_andReturnUnavailable() {
        AiServiceClient client = createClient(1, 0, 1, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .willReturn(WireMock.aResponse().withStatus(502).withBody("bad gateway")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectErrorSatisfies(error -> {
                    GameRecommenderException ex = Assertions.assertInstanceOf(GameRecommenderException.class, error);
                    Assertions.assertEquals(ErrorType.AI_SERVICE_UNAVAILABLE, ex.getErrorType());
                })
                .verify();

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void retryShouldRecover_when502Then200() {
        AiServiceClient client = createClient(1, 0, 2, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("502-then-200")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(502).withBody("bad gateway"))
                .willSetStateTo("SECOND"));

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("502-then-200")
                .whenScenarioStateIs("SECOND")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"ok\",\"provider\":\"test\",\"recommendations\":[]}")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectNextCount(1)
                .verifyComplete();

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void retryShouldRecover_when503Then200() {
        AiServiceClient client = createClient(1, 0, 2, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("503-then-200")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503).withBody("service unavailable"))
                .willSetStateTo("SECOND"));

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("503-then-200")
                .whenScenarioStateIs("SECOND")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"ok\",\"provider\":\"test\",\"recommendations\":[]}")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectNextCount(1)
                .verifyComplete();

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void retryShouldRecover_whenTimeoutThen200() {
        AiServiceClient client = createClient(1, 0, 1, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("timeout-then-200")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withFixedDelay(1_200) // > timeout(1s)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"slow\",\"provider\":\"test\",\"recommendations\":[]}"))
                .willSetStateTo("SECOND"));

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .inScenario("timeout-then-200")
                .whenScenarioStateIs("SECOND")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"ok\",\"provider\":\"test\",\"recommendations\":[]}")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectNextCount(1)
                .verifyComplete();

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void retryShouldNotHappenFor400() {
        AiServiceClient client = createClient(1, 0, 1, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .willReturn(WireMock.aResponse().withStatus(400).withBody("bad request")));

        StepVerifier.create(client.getGameRecommendations(request))
                .expectErrorSatisfies(error -> {
                    GameRecommenderException ex = Assertions.assertInstanceOf(GameRecommenderException.class, error);
                    Assertions.assertEquals(ErrorType.AI_SERVICE_RECOMMENDATION_ERROR, ex.getErrorType());
                })
                .verify();

        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void circuitBreakerShouldOpenAndStopCallingUpstream_failFastOnThirdCall() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);

        // ВАЖНО: retryAttempts=0, чтобы CB открывался от реальных вызовов, а не от retry
        AiServiceClient client = createClient(0, 0, 1, registry);

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .willReturn(WireMock.aResponse().withStatus(503).withBody("service unavailable")));

        // 1-й вызов: upstream hit
        StepVerifier.create(client.getGameRecommendations(request))
                .expectError(GameRecommenderException.class)
                .verify();

        // 2-й вызов: upstream hit, после этого CB должен стать OPEN
        StepVerifier.create(client.getGameRecommendations(request))
                .expectError(GameRecommenderException.class)
                .verify();

        // 3-й вызов: fail-fast, upstream НЕ должен быть вызван
        long startMs = System.currentTimeMillis();

        StepVerifier.create(client.getGameRecommendations(request))
                .expectErrorSatisfies(error -> {
                    GameRecommenderException ex = Assertions.assertInstanceOf(GameRecommenderException.class, error);
                    Assertions.assertEquals(ErrorType.AI_SERVICE_UNAVAILABLE, ex.getErrorType());
                    Assertions.assertTrue(ex.getParams() != null && ex.getParams().length > 0);
                    Assertions.assertEquals("circuit-breaker-open", ex.getParams()[0]);
                })
                .verify();

        long elapsedMs = System.currentTimeMillis() - startMs;
        Assertions.assertTrue(elapsedMs < 500, "Open circuit breaker should fail fast");

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void shouldPropagateRequestIdHeaders() {
        AiServiceClient client = createClient(1, 0, 2, defaultCircuitBreakerRegistry());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(RECOMMEND_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"message\":\"ok\",\"provider\":\"test\",\"recommendations\":[]}")));

        StepVerifier.create(client.getGameRecommendations(request)
                        .contextWrite(ctx -> ctx.put(RequestIdWebFilter.REQUEST_ID_CONTEXT_KEY, "req-123")))
                .expectNextCount(1)
                .verifyComplete();

        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(RECOMMEND_PATH))
                .withHeader("X-Request-Id", WireMock.equalTo("req-123"))
                .withHeader("correlationId", WireMock.equalTo("req-123")));
    }

    private AiServiceClient createClient(
            int retryAttempts,
            long retryDelaySeconds,
            long responseTimeoutSeconds,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        AiServiceProps props = new AiServiceProps(
                "http",
                "localhost",
                wireMockServer.port(),
                RECOMMEND_PATH,
                retryAttempts,
                retryDelaySeconds,
                1_000,
                responseTimeoutSeconds,
                1024 * 1024
        );

        AiServiceClient aiServiceClient = new AiServiceClient(
                org.springframework.web.reactive.function.client.WebClient.create(),
                props,
                circuitBreakerRegistry,
                new ReactiveRetryStrategy()
        );
        aiServiceClient.init();
        return aiServiceClient;
    }

    private CircuitBreakerRegistry defaultCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
}