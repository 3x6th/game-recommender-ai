package ru.perevalov.gamerecommenderai.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.RetryBackoffSpec;
import ru.perevalov.gamerecommenderai.client.props.AiServiceProps;
import ru.perevalov.gamerecommenderai.client.retry.ReactiveRetryStrategy;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.AiRecommendationResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.filter.RequestIdWebFilter;

/**
 * HTTP-клиент для отправки запросов за рекомендациями во внешний AI-сервис.
 *
 * <p>Отвечает за:
 * <ul>
 *     <li>POST-вызов настроенного endpoint рекомендаций</li>
 *     <li>таймаут запроса (20-25 секунд)</li>
 *     <li>один retry только для timeout/502/503</li>
 *     <li>защиту circuit breaker через Resilience4j</li>
 *     <li>прокидывание идентификаторов запроса (X-Request-Id/correlationId)</li>
 *     <li>логирование latency, HTTP-статуса и ошибок</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final WebClient webClient;
    private final AiServiceProps props;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ReactiveRetryStrategy reactiveRetryStrategy;

    private WebClient aiWebClient;
    private URI recommendUri;
    private RetryBackoffSpec retryBackoffSpec;
    private CircuitBreaker circuitBreaker;

    /**
     * Инициализирует URI endpoint, настроенный WebClient и resiliency-компоненты.
     */
    @PostConstruct
    public void init() {
        recommendUri = UriComponentsBuilder.newInstance()
                .scheme(props.scheme())
                .host(props.host())
                .port(props.port())
                .path(props.recommendPath())
                .build()
                .toUri();

        aiWebClient = webClient
                .mutate()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(props.maxInMemorySize()))
                        .build())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(props.responseTimeoutSeconds()))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutMillis())
                ))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        retryBackoffSpec = reactiveRetryStrategy.doAiRecommendRetry(
                props.retryAttempts(),
                props.retryDelaySeconds()
        );
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiServiceRecommend");
    }

    /**
     * Отправляет запрос рекомендаций в AI-сервис с настроенной resiliency-политикой.
     *
     * @param request
     *         payload запроса: сообщение пользователя, теги и контекст Steam-библиотеки
     *
     * @return ответ AI-сервиса с рекомендациями
     */
    public Mono<AiRecommendationResponse> getGameRecommendations(AiContextRequest request) {
        return Mono.deferContextual(ctx -> {
                    String requestId = ctx.getOrDefault(
                            RequestIdWebFilter.REQUEST_ID_CONTEXT_KEY,
                            UUID.randomUUID().toString()
                    );
                    long startNanos = System.nanoTime();

                    return aiWebClient.post()
                            .uri(recommendUri)
                            .header("X-Request-Id", requestId)
                            .header("correlationId", requestId)
                            .bodyValue(request)
                            .exchangeToMono(response -> handleResponse(response, startNanos, requestId))
                            .timeout(Duration.ofSeconds(props.responseTimeoutSeconds()))
                            .retryWhen(retryBackoffSpec)
                            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                            .doOnError(error -> log.error(
                                    "AI recommend call failed: latencyMs={} requestId={} error={}",
                                    latencyMs(startNanos),
                                    requestId,
                                    error.getMessage(),
                                    error
                            ))
                            .onErrorMap(this::mapToDomainError);
                }
        );
    }

    /**
     * Маппит транспортные/клиентские ошибки в доменные исключения приложения.
     */
    private Throwable mapToDomainError(Throwable error) {
        if (error instanceof GameRecommenderException) {
            return error;
        }
        if (Exceptions.isRetryExhausted(error) && error.getCause() != null) {
            return mapToDomainError(error.getCause());
        }

        if (error instanceof TimeoutException) {
            return new GameRecommenderException(ErrorType.AI_SERVICE_UNAVAILABLE, error.getMessage());
        }
        if (error instanceof CallNotPermittedException) {
            return new GameRecommenderException(ErrorType.AI_SERVICE_UNAVAILABLE, "circuit-breaker-open");
        }

        if (error instanceof WebClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 401) {
                return new GameRecommenderException(
                        ErrorType.AI_SERVICE_UNAUTHORIZED,
                        responseException.getResponseBodyAsString()
                );
            }
            if (responseException.getStatusCode().value() == 429) {
                return new GameRecommenderException(
                        ErrorType.AI_SERVICE_RATE_LIMIT,
                        responseException.getResponseBodyAsString()
                );
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new GameRecommenderException(
                        ErrorType.AI_SERVICE_RECOMMENDATION_ERROR,
                        responseException.getResponseBodyAsString()
                );
            }

            return new GameRecommenderException(ErrorType.AI_SERVICE_UNAVAILABLE, responseException.getMessage());
        }

        return new GameRecommenderException(ErrorType.AI_SERVICE_ERROR, error.getMessage());
    }

    /**
     * Обрабатывает ответ upstream-сервиса и логирует статус/latency.
     * Для non-2xx формирует {@link WebClientResponseException} с body для дальнейшей обработки.
     */
    private Mono<AiRecommendationResponse> handleResponse(
            org.springframework.web.reactive.function.client.ClientResponse response,
            long startNanos,
            String requestId) {
        HttpStatusCode statusCode = response.statusCode();
        if (statusCode.is2xxSuccessful()) {
            return response.bodyToMono(AiRecommendationResponse.class)
                    .doOnSuccess(body -> log.info(
                            "AI recommend upstream success: status={} latencyMs={} requestId={} recommendations={}",
                            statusCode.value(),
                            latencyMs(startNanos),
                            requestId,
                            body != null && body.getRecommendations() != null
                                    ? body.getRecommendations().size() : 0
                    ));
        }

        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    log.warn(
                            "AI recommend upstream non-2xx: status={} latencyMs={} requestId={} body={}",
                            statusCode.value(),
                            latencyMs(startNanos),
                            requestId,
                            body
                    );
                    return Mono.error(WebClientResponseException.create(
                            statusCode.value(),
                            HttpStatus.valueOf(statusCode.value()).getReasonPhrase(),
                            null,
                            body.getBytes(),
                            null
                    ));
                });
    }

    /**
     * Возвращает прошедшее время в миллисекундах от стартовой точки.
     */
    private long latencyMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
