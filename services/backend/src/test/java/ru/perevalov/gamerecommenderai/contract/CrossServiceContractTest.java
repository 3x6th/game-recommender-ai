package ru.perevalov.gamerecommenderai.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.client.props.AiGrpcClientProps;
import ru.perevalov.gamerecommenderai.dto.AiChatHistoryMessage;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.grpc.JavaToolsServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.ReactorGameRecommenderServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamAppRequest;
import ru.perevalov.gamerecommenderai.grpc.SteamAppResponse;
import ru.perevalov.gamerecommenderai.interceptor.GrpcRequestIdClientInterceptor;
import ru.perevalov.gamerecommenderai.mapper.GrpcErrorMapper;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

class CrossServiceContractTest {

    private static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> REQUEST_ID_CONTEXT =
            Context.key("contract-request-id");

    private static final DeterministicToolsService TOOLS_SERVICE =
            new DeterministicToolsService();

    private static Server toolsServer;
    private static ManagedChannel aiChannel;
    private static GameRecommenderGrpcClient client;

    @BeforeAll
    static void setUpContract() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(
                System.getenv().getOrDefault("RUN_CROSS_SERVICE_CONTRACT", "false")));

        int toolsPort = Integer.parseInt(
                System.getenv().getOrDefault("CONTRACT_JAVA_TOOLS_PORT", "19091"));
        int aiPort = Integer.parseInt(
                System.getenv().getOrDefault("CONTRACT_AI_GRPC_PORT", "19090"));

        toolsServer = ServerBuilder.forPort(toolsPort)
                .addService(io.grpc.ServerInterceptors.intercept(
                        TOOLS_SERVICE,
                        new RequestIdCaptureInterceptor()))
                .build()
                .start();

        aiChannel = ManagedChannelBuilder.forAddress("127.0.0.1", aiPort)
                .usePlaintext()
                .build();
        ReactorGameRecommenderServiceGrpc.ReactorGameRecommenderServiceStub stub =
                ReactorGameRecommenderServiceGrpc.newReactorStub(
                        ClientInterceptors.intercept(
                                aiChannel,
                                new GrpcRequestIdClientInterceptor(
                                        "RequestID")));

        client = new GameRecommenderGrpcClient(
                new GrpcMapper(),
                new GrpcErrorMapper(),
                CircuitBreaker.ofDefaults("cross-service-contract"),
                new SimpleMeterRegistry(),
                new AiGrpcClientProps(5, 0, 0));
        ReflectionTestUtils.setField(client, "gameRecommenderServiceStub", stub);
    }

    @AfterAll
    static void tearDownContract() throws Exception {
        if (aiChannel != null) {
            aiChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (toolsServer != null) {
            toolsServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void cardsRoundTripPreservesResponseContract() {
        RecommendationResponse response = invoke(request("contract:cards", "cards-request"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Cards ready");
        assertThat(response.getReasoning()).isEqualTo("Contract reasoning");
        assertThat(response.getProvider()).isEqualTo("DeepSeekContractModel");
        assertThat(response.getRecommendationsCount()).isEqualTo(1);
        assertThat(response.getRecommendations(0).getTitle()).isEqualTo("Portal 2");
        assertThat(response.getRecommendations(0).getPlatformsList()).containsExactly("PC");
        assertThat(response.getRecommendations(0).getRating()).isEqualTo(9.5);
    }

    @Test
    void textOnlyRoundTripPreservesAssistantHistory() {
        AiContextRequest request = request("contract:text", "text-request");
        request.setHistory(List.of(
                new AiChatHistoryMessage(
                        AiChatHistoryMessage.Role.USER,
                        "Recommend a puzzle"),
                new AiChatHistoryMessage(
                        AiChatHistoryMessage.Role.ASSISTANT,
                        "Portal 2")));

        RecommendationResponse response = invoke(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("History preserved: Portal 2");
        assertThat(response.getRecommendationsList()).isEmpty();
    }

    @Test
    void invalidStructuredOutputIsRepairedAcrossRealGrpc() {
        RecommendationResponse response = invoke(request("contract:repair", "repair-request"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Repaired reply");
        assertThat(response.getRecommendationsList()).isEmpty();
    }

    @Test
    void pythonAgentCallsBothJavaToolsAndReturnsFinalCard() {
        int searchBefore = TOOLS_SERVICE.searchCalls.get();
        int detailsBefore = TOOLS_SERVICE.detailsCalls.get();

        RecommendationResponse response = invokeWithTrace(
                request("contract:tool-chain", "tool-chain-protobuf-request"),
                "e2e-observability-trace");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getRecommendations(0).getTitle()).isEqualTo("Factorio");
        assertThat(response.getReasoning()).isEqualTo("Verified by Java tools");
        assertThat(TOOLS_SERVICE.searchCalls.get() - searchBefore).isEqualTo(1);
        assertThat(TOOLS_SERVICE.detailsCalls.get() - detailsBefore).isEqualTo(1);
        assertThat(TOOLS_SERVICE.lastRequestId.get())
                .isEqualTo("e2e-observability-trace");
    }

    @Test
    void toolNotFoundBecomesSuccessfulConversationalReply() {
        int before = TOOLS_SERVICE.detailsCalls.get();

        RecommendationResponse response = invoke(
                request("contract:tool-not-found", "not-found-request"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Steam app was not found");
        assertThat(TOOLS_SERVICE.detailsCalls.get() - before).isEqualTo(1);
    }

    @Test
    void toolUnavailableRetriesOnceAndDoesNotBreakUserRequest() {
        int before = TOOLS_SERVICE.detailsCalls.get();

        RecommendationResponse response = invoke(
                request("contract:tool-unavailable", "unavailable-request"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Catalog is temporarily unavailable");
        assertThat(TOOLS_SERVICE.detailsCalls.get() - before).isEqualTo(2);
    }

    @Test
    void toolDeadlineRetriesOnceAndDoesNotBreakUserRequest() {
        int before = TOOLS_SERVICE.searchCalls.get();

        RecommendationResponse response = invoke(
                request("contract:tool-deadline", "deadline-request"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Catalog deadline exceeded");
        assertThat(TOOLS_SERVICE.searchCalls.get() - before).isEqualTo(2);
    }

    @Test
    void providerFailureAndGraphLoopLimitUseControlledErrorEnvelope() {
        RecommendationResponse providerFailure = invoke(
                request("contract:controlled-error", "provider-error-request"));
        RecommendationResponse loopLimit = invoke(
                request("contract:loop-limit", "loop-limit-request"));

        assertThat(providerFailure.getSuccess()).isFalse();
        assertThat(providerFailure.getMessage())
                .isEqualTo("AI recommendation is temporarily unavailable")
                .doesNotContain("private contract provider failure");
        assertThat(loopLimit.getSuccess()).isFalse();
        assertThat(loopLimit.getMessage())
                .isEqualTo("AI recommendation is temporarily unavailable")
                .doesNotContain("tool iteration limit");
    }

    private static RecommendationResponse invoke(AiContextRequest request) {
        return client.getGameRecommendations(Mono.just(request))
                .block(Duration.ofSeconds(10));
    }

    private static RecommendationResponse invokeWithTrace(
            AiContextRequest request,
            String requestId) {
        MDC.put("RequestID", requestId);
        try {
            return invoke(request);
        } finally {
            MDC.remove("RequestID");
        }
    }

    private static AiContextRequest request(String message, String requestId) {
        return AiContextRequest.builder()
                .userMessage(message)
                .selectedTags(new String[]{"Puzzle"})
                .profileSummary("{}")
                .chatId("contract-chat")
                .agentId("contract-agent")
                .requestId(requestId)
                .correlationId("contract-correlation")
                .language("en")
                .maxResults(3)
                .history(List.of())
                .build();
    }

    private static final class RequestIdCaptureInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            String requestId = headers.get(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = "unknown";
            }
            Context context = Context.current().withValue(REQUEST_ID_CONTEXT, requestId);
            return Contexts.interceptCall(context, call, headers, next);
        }
    }

    private static final class DeterministicToolsService
            extends JavaToolsServiceGrpc.JavaToolsServiceImplBase {

        private final AtomicInteger searchCalls = new AtomicInteger();
        private final AtomicInteger detailsCalls = new AtomicInteger();
        private final AtomicReference<String> lastRequestId = new AtomicReference<>();

        @Override
        public void searchGames(
                SearchGamesRequest request,
                StreamObserver<SearchGamesResponse> responseObserver) {
            searchCalls.incrementAndGet();
            lastRequestId.set(REQUEST_ID_CONTEXT.get());
            if ("slow".equals(request.getQuery())) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    responseObserver.onError(Status.CANCELLED.asRuntimeException());
                    return;
                }
            }
            SteamAppResponse game = SteamAppResponse.newBuilder()
                    .setAppId("Factorio".equals(request.getQuery()) ? 427520 : 620)
                    .setName("Factorio".equals(request.getQuery()) ? "Factorio" : "Portal 2")
                    .build();
            responseObserver.onNext(SearchGamesResponse.newBuilder().addGames(game).build());
            responseObserver.onCompleted();
        }

        @Override
        public void getSteamAppDetails(
                SteamAppRequest request,
                StreamObserver<SteamAppResponse> responseObserver) {
            detailsCalls.incrementAndGet();
            lastRequestId.set(REQUEST_ID_CONTEXT.get());
            if (request.getAppId() == 999999999) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                return;
            }
            if (request.getAppId() == 440) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
                return;
            }
            responseObserver.onNext(SteamAppResponse.newBuilder()
                    .setAppId(request.getAppId())
                    .setName(request.getAppId() == 427520 ? "Factorio" : "Portal 2")
                    .setDescription("Verified Java contract data")
                    .addGenres("Automation")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getSimilarGames(
                SimilarGamesRequest request,
                StreamObserver<SimilarGamesResponse> responseObserver) {
            responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException());
        }
    }
}
