package ru.perevalov.gamerecommenderai.client;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.RetryBackoffSpec;
import ru.perevalov.gamerecommenderai.client.props.SteamApiProps;
import ru.perevalov.gamerecommenderai.client.retry.ReactiveRetryStrategy;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.util.UrlHelper;

@Component
@Slf4j
@RequiredArgsConstructor
public class SteamApiClient {

    private final SteamApiProps props;
    private final WebClient webClient;
    private WebClient customSteamApiWebClient;
    private URI uri;
    private RetryBackoffSpec retryBackoffSpec;
    private final ReactiveRetryStrategy reactiveRetryStrategy;

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

        customSteamApiWebClient = webClient
                .mutate()
                .exchangeStrategies(ExchangeStrategies.builder()
                                                      .codecs(configurer -> configurer
                                                              .defaultCodecs()
                                                              .maxInMemorySize(props.maxInMemorySize()))
                                                      .build())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                  .responseTimeout(Duration.ofMinutes(
                                          props.durationOfMinutes()))
                                  .option(
                                          ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                          props.connectTimeoutMillis()
                                  )
                ))
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
        retryBackoffSpec = reactiveRetryStrategy.doBackoffRetry(
                props.retryAttempts(),
                props.retryDelaySeconds(),
                props.jitter()
        );
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
     *
     * @throws GameRecommenderException
     *         if fetching fails
     */
    //TODO: @Transactional теперь д. б. реактивная (spring-data-r2dbc)
    public Mono<SteamAppResponseDto> fetchSteamApps() {
        log.info("Start fetchSteamApps method... ");
        long startTime = System.currentTimeMillis();
        return customSteamApiWebClient.get()
                                      .uri(uri)
                                      .retrieve()
                                      .bodyToMono(SteamAppResponseDto.class)
                                      .retryWhen(retryBackoffSpec)
                                      .doOnSuccess(resp -> {
                                          long endTime = System.currentTimeMillis();
                                          log.info(
                                                  "Response received successfully. Fetched {} apps. Time taken: {} ms",
                                                  resp.appList().apps().size(),
                                                  endTime - startTime
                                          );
                                      })
                                      .doOnError(error -> log.error(
                                              "Error fetching Steam apps: {}",
                                              error.getMessage()
                                      ))
                                      .onErrorMap(e -> new GameRecommenderException(
                                              ErrorType.STEAM_API_FETCH_GAMES_LIST_ERROR,
                                              uri
                                      ));
    }

}