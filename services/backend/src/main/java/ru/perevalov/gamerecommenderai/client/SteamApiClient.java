package ru.perevalov.gamerecommenderai.client;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import ru.perevalov.gamerecommenderai.config.SteamApiProps;
import ru.perevalov.gamerecommenderai.dto.steam.GameRootResponseDto;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class SteamApiClient {

    private String uri;

    private final SteamApiProps props;

    @PostConstruct
    public void init() {
        uri = UriComponentsBuilder.newInstance()
                .scheme(props.scheme())
                .host(props.host())
                .path(props.getAppList())
                .build()
                .toUriString();
        log.debug("Full URI built: {}", uri);
    }

    public GameRootResponseDto fetchSteamApps() {
        log.debug("Start fetchSteamApps method... ");
        try {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(props.maxInMemorySize()))
                    .build();

            WebClient.Builder builder = WebClient.builder()
                    .exchangeStrategies(strategies)
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create()
                                    .responseTimeout(Duration.ofMinutes(props.durationOfMinutes()))
                                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeoutMillis())
                    ))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);


            RetryBackoffSpec retryBackoffSpec = Retry.backoff(props.retryAttempts(), Duration.ofSeconds(props.retryDelaySeconds()))
                    .jitter(props.jitter())
                    .doBeforeRetry(r -> log.warn("Starting retry attempt {} due to: {}", r.totalRetries(), r.failure().getMessage()))
                    .doAfterRetry(rs -> log.debug("Retry attempt finished, total attempts: {}", rs.totalRetries()))
                    .filter(this::shouldRetry);

            WebClient largeTimeoutClient = builder.build();

            GameRootResponseDto response = largeTimeoutClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(GameRootResponseDto.class)
                    .doOnNext(resp -> log.debug("Response parsed successfully: fetched {} apps.", resp.appList().apps().size()))
                    .doOnError(error -> log.error("Request or parsing failed: {}", error.getMessage()))
                    .retryWhen(retryBackoffSpec)
                    .block();

            log.debug("fetchSteamApps completed successfully.");
            return response;
        } catch (Exception e) {
            log.error("Error in fetchSteamApps: {}", e.getMessage());
            throw new GameRecommenderException(ErrorType.STEAM_API_FETCH_GAMES_LIST_ERROR);
        }
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            return e.getStatusCode().is5xxServerError();
        }
        return true;
    }

}
