package ru.perevalov.gamerecommenderai.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Класс конфигурации для настройки WebClient с ограничением запросов и логированием.
 * <p>
 * Настраивает WebClient для работы с Steam API, добавляя фильтры для ограничения
 * запросов и логирования. Обеспечивает соблюдение лимитов Steam API и мониторинг запросов.
 * </p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebClientConfig {

    @Bean
    public WebClient steamWebClient(Bucket apiBucket, SteamProps props) {
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", props.apiKey())
                .filter(rateLimiterFilter(apiBucket))
                .filter(loggingFilter())
                .build();
    }

    /**
     * Создает фильтр для ограничения запросов на основе корзины токенов.
     * <p>
     * Фильтр проверяет доступность токенов перед выполнением запроса.
     * Если токены доступны - запрос выполняется, если нет - возвращается ошибка 429.
     * </p>
     *
     * @param bucket корзина токенов для проверки ограничений
     * @return фильтр ограничения запросов
     */
    private ExchangeFilterFunction rateLimiterFilter(Bucket bucket) {
        return (request, next) -> {
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                log.debug("Request allowed. Remaining tokens: {}", probe.getRemainingTokens());

                return next.exchange(request);
            } else {
                long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());

                log.warn("Rate limit exceeded. Wait {} seconds", waitSeconds);

                return Mono.error(new RuntimeException(
                        "Rate limit exceeded. Try again in " + waitSeconds + " seconds"
                ));
            }
        };
    }

    private ExchangeFilterFunction loggingFilter() {
        return (request, next) -> {
            log.info("Sending request to Steam API: {} {}", request.method(), request.url());

            return next.exchange(request)
                    .doOnSuccess(response -> log.debug("Response status: {}", response.statusCode()))
                    .doOnError(error -> log.error("Request failed: {}", error.getMessage()));
        };
    }
}
