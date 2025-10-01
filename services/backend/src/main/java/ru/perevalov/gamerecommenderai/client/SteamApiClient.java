package ru.perevalov.gamerecommenderai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
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
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.mapper.SteamAppMapper;
import ru.perevalov.gamerecommenderai.repository.SteamAppRepository;
import ru.perevalov.gamerecommenderai.util.UrlHelper;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SteamApiClient {
    private final SteamApiProps props;
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final ObjectMapper objectMapper;
    private final SteamAppRepository steamAppRepository;
    private final SteamAppMapper steamAppMapper;

    private URI uri;
    private WebClient customWebClient;
    private RetryBackoffSpec retryBackoffSpec;

    private static final String CACHE_KEY = "steam_apps";
    private static final int BATCH_SIZE = 5000;

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

        customWebClient = WebClient.builder()
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
        try {
            SteamAppResponseDto cached = getFromCache();
            if (cached != null) {
                log.info("Returning Steam apps from Redis cache");
                return cached;
            }

            SteamAppResponseDto response = customWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(SteamAppResponseDto.class)
                    .doOnNext(resp -> log.info("Response received successfully. Fetched {} apps.", resp.appList().apps().size()))
                    .doOnError(error -> log.error("Request failed: {}", error.getMessage()))
                    .retryWhen(retryBackoffSpec)
                    .block();

            saveToCache(response);
            saveToDatabase(response);

            log.debug("fetchSteamApps completed successfully.");
            return response;
        } catch (Exception e) {
            log.error("Error fetching Steam apps: {}", e.getMessage());
            throw new GameRecommenderException(ErrorType.STEAM_API_FETCH_GAMES_LIST_ERROR);
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

    private SteamAppResponseDto getFromCache() {
        try {
            byte[] cached = redisConnection.sync().get(CACHE_KEY.getBytes(StandardCharsets.UTF_8));
            if (cached != null) {
                return objectMapper.readValue(cached, SteamAppResponseDto.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read from Redis cache", e);
        }
        return null;
    }

    private void saveToCache(SteamAppResponseDto response) {
        if (response == null) return;
        try {
            redisConnection.sync().set(CACHE_KEY.getBytes(StandardCharsets.UTF_8),
                    objectMapper.writeValueAsBytes(response));
            log.info("Steam apps saved to Redis cache");
        } catch (Exception e) {
            log.warn("Failed to save Steam apps to Redis cache", e);
        }
    }

    private void saveToDatabase(SteamAppResponseDto response) {
        if (response == null || response.appList() == null) {
            log.warn("No Steam apps to save to database.");
            return;
        }

        List<SteamAppResponseDto.AppList.App> fullApps = response.appList().apps();

        int testSize = 15000;
        List<SteamAppResponseDto.AppList.App> apps = fullApps.size() > testSize ? fullApps.subList(0, testSize) : fullApps;

        log.info("Starting batch save of {} Steam apps to database.", apps.size());
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < apps.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, apps.size());
            List<SteamAppResponseDto.AppList.App> batchApps = apps.subList(i, end);

            List<SteamAppEntity> entitiesBatch = new ArrayList<>(batchApps.size());
            for (SteamAppResponseDto.AppList.App app : batchApps) {
                entitiesBatch.add(steamAppMapper.toEntity(app));
            }

            long batchStart = System.currentTimeMillis();
            steamAppRepository.batchInsert(entitiesBatch);
            long batchEnd = System.currentTimeMillis();

            log.info("Saved batch of {} in {} ms. Progress: {}/{}", batchApps.size(), (batchEnd - batchStart), end, apps.size());
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("Saved {} Steam apps to database in {} ms (average ~{} ms per record).",
                apps.size(), duration, (double) duration / apps.size());
    }

}