package ru.perevalov.gamerecommenderai.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import ru.perevalov.gamerecommenderai.client.SteamStoreClient;
import ru.perevalov.gamerecommenderai.config.GrpcToolsProps;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.grpc.ReactorJavaToolsServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamAppRequest;
import ru.perevalov.gamerecommenderai.grpc.SteamAppResponse;
import ru.perevalov.gamerecommenderai.interceptor.GrpcRequestIdServerInterceptor;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

/**
 * Реактивный gRPC-сервер internal Tools API (PCAI-122): источник данных о Steam-играх
 * для Python AI-агента (PCAI-129), который дёргает эти RPC как LangChain tools.
 * <p>
 * Публикуемые методы:
 * <ul>
 *   <li>{@code GetSteamAppDetails} — детали игры по appId из Steam Store API.</li>
 *   <li>{@code SearchGames} — fuzzy-поиск по имени поверх {@code pg_trgm} индекса
 *       (см. {@link ru.perevalov.gamerecommenderai.repository.SteamAppRepository#searchByNameLike}).</li>
 *   <li>{@code GetSimilarGames} — зарезервирован, сейчас возвращает
 *       {@link Status#UNIMPLEMENTED}, реализация вынесена в PCAI-135.</li>
 * </ul>
 * <p>
 * Лимиты и таймауты — через {@link GrpcToolsProps} ({@code app.grpc.tools.*}).
 * Маппинг DTO ↔ protobuf — в {@link GrpcMapper}. Ошибки сервиса транслируются
 * в gRPC {@link Status} через {@link #mapToGrpcError}.
 * <p>
 * Propagation {@code requestId}: входящий {@code x-request-id} из gRPC metadata
 * кладётся в {@link io.grpc.Context} интерсептором {@link GrpcRequestIdServerInterceptor},
 * здесь он разово читается на входе в RPC и пробрасывается в Reactor Context
 * через {@code .contextWrite(...)} — дальше {@code ReactorMdcConfiguration}
 * (PR #45) синхронизирует его в MDC на каждом операторе.
 *
 * @see GrpcRequestIdServerInterceptor
 * @see GrpcToolsProps
 * @see GrpcMapper
 */
@GrpcService
@Slf4j
public class JavaToolsServiceImpl extends ReactorJavaToolsServiceGrpc.JavaToolsServiceImplBase {

    private static final String REACTOR_CTX_REQUEST_ID_KEY = "requestId";

    /**
     * Jira-ссылка на задачу-блокер, под которую вынесли реализацию getSimilarGames —
     * у Steam нет готового "similar games" эндпоинта, нужен отдельный дизайн
     * источника жанров (см. описание PCAI-135).
     */
    private static final String SIMILAR_GAMES_TICKET = "PCAI-135";

    private final SteamStoreClient steamStoreClient;
    private final GameService gameService;
    private final GrpcMapper mapper;
    private final GrpcToolsProps props;

    private final String requestIdLoggingParam;

    public JavaToolsServiceImpl(SteamStoreClient steamStoreClient,
                                GameService gameService,
                                GrpcMapper mapper,
                                GrpcToolsProps props,
                                @Value("${requestid.logging.param}") String requestIdLoggingParam) {
        this.steamStoreClient = steamStoreClient;
        this.gameService = gameService;
        this.mapper = mapper;
        this.props = props;
        this.requestIdLoggingParam = requestIdLoggingParam;
    }

    @Override
    public Mono<SteamAppResponse> getSteamAppDetails(Mono<SteamAppRequest> requestMono) {
        String requestId = currentRequestId();

        return requestMono
                .doOnNext(req -> log.info("gRPC GetSteamAppDetails[{}] appId={}", requestId, req.getAppId()))
                .flatMap(req -> steamStoreClient.fetchGameDetails(String.valueOf(req.getAppId()))
                                                .flatMap(dto -> Mono.justOrEmpty(mapper.toSteamAppResponse(dto))))
                .switchIfEmpty(Mono.error(() -> Status.NOT_FOUND
                        .withDescription("Steam app details not found or empty payload")
                        .asRuntimeException()))
                .onErrorResume(this::mapToGrpcError)
                .contextWrite(requestContextOf(requestId));
    }

    @Override
    public Mono<SimilarGamesResponse> getSimilarGames(Mono<SimilarGamesRequest> requestMono) {
        String requestId = currentRequestId();

        return requestMono
                .doOnNext(req -> log.info("gRPC GetSimilarGames[{}] appId={} limit={} — not yet implemented, see {}",
                        requestId, req.getAppId(), req.getLimit(), SIMILAR_GAMES_TICKET))
                .<SimilarGamesResponse>flatMap(req -> Mono.error(Status.UNIMPLEMENTED
                        .withDescription("getSimilarGames is not implemented yet — tracked in " + SIMILAR_GAMES_TICKET)
                        .asRuntimeException()))
                .contextWrite(requestContextOf(requestId));
    }

    // TODO(PCAI-136): response cache в Redis (namespace grpc:search:<query>:<limit>)
    //  с коротким TTL — у LLM-агента низкая энтропия запросов, одни и те же query
    //  повторяются десятками раз. Индекс pg_trgm уже закрывает cache miss.
    @Override
    public Mono<SearchGamesResponse> searchGames(Mono<SearchGamesRequest> requestMono) {
        String requestId = currentRequestId();

        return requestMono
                .flatMap(req -> {
                    int limit = props.clampLimit(req.getLimit());
                    String query = req.getQuery() == null ? "" : req.getQuery().trim();
                    log.info("gRPC SearchGames[{}] query='{}' limit={}", requestId, query, limit);

                    if (query.isBlank()) {
                        return Mono.just(SearchGamesResponse.getDefaultInstance());
                    }

                    return gameService.searchByQuery(query, limit)
                                      .map(entities -> {
                                          SearchGamesResponse.Builder builder = SearchGamesResponse.newBuilder();
                                          for (var e : entities) {
                                              builder.addGames(mapper.toSteamAppResponse(e.getAppid(), e.getName()));
                                          }
                                          return builder.build();
                                      });
                })
                .onErrorResume(this::mapToGrpcError)
                .contextWrite(requestContextOf(requestId));
    }

    /**
     * Возвращает активный {@code requestId} из {@link io.grpc.Context}.
     * Читать можно только синхронно из RPC-метода reactor-grpc base-класса —
     * на этом этапе gRPC-нить ещё «привязана» к {@link io.grpc.Context}, который
     * выставил {@link GrpcRequestIdServerInterceptor}. После первого реактивного
     * оператора этот контекст уже недоступен, поэтому ловим в локальную переменную
     * и дальше работаем через Reactor Context.
     */
    private String currentRequestId() {
        String requestId = GrpcRequestIdServerInterceptor.REQUEST_ID_CONTEXT.get();
        return requestId == null ? GrpcRequestIdServerInterceptor.REQUEST_ID_FALLBACK : requestId;
    }

    private Context requestContextOf(String requestId) {
        return Context.of(
                REACTOR_CTX_REQUEST_ID_KEY, requestId,
                requestIdLoggingParam, requestId);
    }

    /**
     * Маппит внутренние исключения в осмысленный {@link StatusRuntimeException}:
     * доменные ошибки Steam «не найдено» → {@link Status#NOT_FOUND},
     * уже упакованные {@link StatusRuntimeException} пропускаются без изменений,
     * остальное уходит как {@link Status#INTERNAL} с описанием (клиенту без stacktrace,
     * stacktrace остаётся в логах).
     */
    private <T> Mono<T> mapToGrpcError(Throwable error) {
        if (error instanceof StatusRuntimeException sre) {
            return Mono.error(sre);
        }
        if (error instanceof GameRecommenderException ex
                && (ex.getErrorType() == ErrorType.STEAM_APP_DETAILS_NOT_FOUND
                || ex.getErrorType() == ErrorType.STEAM_DATA_IN_APP_DETAILS_NOT_FOUND)) {
            log.warn("gRPC tools: Steam returned no data, mapping to NOT_FOUND: {}", ex.getMessage());
            return Mono.error(Status.NOT_FOUND
                    .withDescription(ex.getMessage())
                    .asRuntimeException());
        }
        log.error("gRPC tools: unexpected error, mapping to INTERNAL", error);
        return Mono.error(Status.INTERNAL
                .withDescription(error.getClass().getSimpleName() + ": " + error.getMessage())
                .asRuntimeException());
    }
}
