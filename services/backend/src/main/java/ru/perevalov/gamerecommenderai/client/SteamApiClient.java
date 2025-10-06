package ru.perevalov.gamerecommenderai.client;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import ru.perevalov.gamerecommenderai.client.props.SteamApiProps;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.util.UrlHelper;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class SteamApiClient {
    private final SteamApiProps props;
    private final WebClient webClient;
    private WebClient customSteamApiWebClient;
    private URI uri;
    private RetryBackoffSpec retryBackoffSpec;

    /**
     * Initializes the client:
     * <ul>
     *     <li>Builds the base URI for fetching Steam apps.</li>
     *     <li>Configures a custom WebClient with timeouts and max memory.</li>
     *     <li>Sets up a retry strategy for transient errors.</li>
     * </ul>
     */
    @PostConstruct
    public void init() {
        uri = UrlHelper.buildUri(
                props.scheme(),
                props.host(),
                props.getAppListPath(),
                null
        );

        customSteamApiWebClient = webClient.mutate()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs()
                                .maxInMemorySize(props.maxInMemorySize()))
                        .build())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofMinutes(props.durationOfMinutes()))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutMillis())
                ))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        retryBackoffSpec = Retry.backoff(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds()))
                .jitter(props.jitter())
                .doBeforeRetry(r -> log.warn("Starting retry attempt {} due to: {}", r.totalRetries(), r.failure().getMessage()))
                .doAfterRetry(rs -> log.debug("Retry attempt finished, total attempts: {}", rs.totalRetries()))
                .filter(this::shouldRetry);
    }

    /**
     * Fetches the list of Steam apps.
     *
     * <p>This method uses a custom WebClient configured with:
     * <ul>
     *     <li>Memory limits for large responses</li>
     *     <li>Timeouts for slow responses</li>
     *     <li>Retry strategy for server errors and transient failures</li>
     * </ul>
     *
     * @return {@link SteamAppResponseDto} containing the list of Steam apps
     * @throws GameRecommenderException if fetching fails
     */
    @Transactional
    public SteamAppResponseDto fetchSteamApps() {
        log.debug("Start fetchSteamApps method... ");
        long startTime = System.currentTimeMillis();
        try {
            SteamAppResponseDto response = customSteamApiWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(SteamAppResponseDto.class)
                    .doOnNext(resp -> log.info("Response received successfully. Fetched {} apps.", resp.appList().apps().size()))
                    .doOnError(error -> log.error("Request failed: {}", error.getMessage()))
                    .retryWhen(retryBackoffSpec)
                    .block();

            long endTime = System.currentTimeMillis();
            log.debug("Fetching Steam Apps completed successfully. Time taken: {} ms", endTime - startTime);
            return response;
        } catch (Exception e) {
            log.error("Error fetching Steam apps: {}", e.getMessage());
            throw new GameRecommenderException(ErrorType.STEAM_API_FETCH_GAMES_LIST_ERROR, uri);
        }
    }

    /**
     * Determines whether a failed request should be retried.
     *
     * @param throwable the exception from the failed request
     * @return true if the request should be retried
     */
    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }

}