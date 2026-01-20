package ru.perevalov.gamerecommenderai.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.perevalov.gamerecommenderai.exception.ErrorResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Slf4j
@Component
@Order(0)
public class RateLimitWebFilter implements WebFilter {
    private static final Duration REFILL_PERIOD = Duration.ofHours(1);

    private final LettuceBasedProxyManager<byte[]> proxyManager;
    private final ObjectMapper objectMapper;

    @Value("${performance.rate-limiter.role.limit.of-hour.GUEST_USER:5}")
    private long guestLimitPerHour;

    @Value("${performance.rate-limiter.role.limit.of-hour.USER:20}")
    private long userLimitPerHour;

    public RateLimitWebFilter(LettuceBasedProxyManager<byte[]> proxyManager,
                              ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        RequestIdentity identity = exchange.getAttribute(RequestIdentity.EXCHANGE_ATTRIBUTE);
        if (identity == null) {
            identity = RequestIdentity.anonymous();
        }

        String key = buildKey(exchange, identity);
        BucketConfiguration configuration = buildConfiguration(identity.role());

        return Mono.fromCallable(() -> {
                    Bucket bucket = proxyManager.builder()
                            .build(key.getBytes(StandardCharsets.UTF_8), () -> configuration);
                    return bucket.tryConsumeAndReturnRemaining(1);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(probe -> handleProbe(exchange, chain, probe));
    }

    private Mono<Void> handleProbe(ServerWebExchange exchange, WebFilterChain chain, ConsumptionProbe probe) {
        if (probe.isConsumed()) {
            exchange.getResponse().getHeaders().set(
                    "X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens())
            );
            return chain.filter(exchange);
        }

        long waitSeconds = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        String message = ErrorType.API_RATE_LIMIT_EXCEEDED.getDescription() + " Retry after " + waitSeconds + "s.";
        ErrorResponse errorResponse = ErrorResponse.build(
                ErrorType.API_RATE_LIMIT_EXCEEDED.getStatus().value(),
                exchange.getRequest().getPath().value(),
                ErrorType.API_RATE_LIMIT_EXCEEDED,
                message
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(ErrorType.API_RATE_LIMIT_EXCEEDED.getStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(waitSeconds));
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(bytes)));
    }

    private BucketConfiguration buildConfiguration(UserRole role) {
        long limit = role == UserRole.USER ? userLimitPerHour : guestLimitPerHour;
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit)
                        .refillGreedy(limit, REFILL_PERIOD))
                .build();
    }

    private String buildKey(ServerWebExchange exchange, RequestIdentity identity) {
        if (identity.role() == UserRole.USER) {
            if (identity.steamId() != null) {
                return "rl:USER:" + identity.steamId();
            }
            if (identity.sessionId() != null) {
                return "rl:USER:" + identity.sessionId();
            }
        }

        if (identity.sessionId() != null) {
            return "rl:GUEST:" + identity.sessionId();
        }

        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        return "rl:GUEST:ip:" + ip;
    }

    private boolean shouldSkip(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return true;
        }
        return path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/actuator")
                || path.startsWith("/api/v1/auth");
    }
}
