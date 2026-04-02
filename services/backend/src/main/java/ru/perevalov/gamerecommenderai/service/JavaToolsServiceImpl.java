package ru.perevalov.gamerecommenderai.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.client.SteamStoreClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;
import ru.perevalov.gamerecommenderai.grpc.JavaToolsServiceGrpc;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SearchGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesRequest;
import ru.perevalov.gamerecommenderai.grpc.SimilarGamesResponse;
import ru.perevalov.gamerecommenderai.grpc.SteamAppRequest;
import ru.perevalov.gamerecommenderai.grpc.SteamAppResponse;
import ru.perevalov.gamerecommenderai.interceptor.GrpcRequestIdServerInterceptor;
import ru.perevalov.gamerecommenderai.mapper.GrpcMapper;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

@GrpcService
@Slf4j
public class JavaToolsServiceImpl extends JavaToolsServiceGrpc.JavaToolsServiceImplBase {

    private final SteamStoreClient steamStoreClient;

    private final GameService gameService;

    private final GrpcMapper mapper;

    @Value("${app.recommender.prompt.top-by-playtime-list-size:20}")
    private int topByPlaytimeListSize;

    public JavaToolsServiceImpl(SteamStoreClient steamStoreClient,
                                GameService gameService,
                                GrpcMapper mapper) {
        this.steamStoreClient = steamStoreClient;
        this.gameService = gameService;
        this.mapper = mapper;
    }

    @Override
    public void getSteamAppDetails(SteamAppRequest request,
                                   StreamObserver<SteamAppResponse> responseObserver) {

        int appId = request.getAppId();
        String requestId = GrpcRequestIdServerInterceptor.REQUEST_ID_CONTEXT.get();

        log.info("Получен запрос {} на предоставление деталей игры {}", requestId, appId);

        steamStoreClient.fetchGameDetails(String.valueOf(appId))
                .contextWrite(ctx -> ctx
                        .put("requestId", requestId)
                        .put("requestIdLoggingParam", requestId))
                .map(mapper::toSteamAppResponse)
                .blockOptional(Duration.ofSeconds(3))
                .ifPresentOrElse(
                        response -> {
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND))
                );
    }

    @Override
    public void getSimilarGames(SimilarGamesRequest request,
                                StreamObserver<SimilarGamesResponse> responseObserver) {

        int appId = request.getAppId();
        int limit = request.getLimit();
        String requestId = GrpcRequestIdServerInterceptor.REQUEST_ID_CONTEXT.get();

        int finalLimit = (limit > 0 && limit <= 100) ? limit : topByPlaytimeListSize;

        log.info("Получен запрос {} на поиск игр подобных {} с лимитом {}", requestId, appId, finalLimit);

        try {
            SteamGameDetailsResponseDto sourceGame = steamStoreClient
                    .fetchGameDetails(String.valueOf(appId))
                    .contextWrite(ctx -> ctx.put("requestId", requestId)
                            .put("requestIdLoggingParam", requestId))
                    .blockOptional(Duration.ofSeconds(3))
                    .orElse(null);

            if (sourceGame == null || !sourceGame.success()) {
                responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND));
                return;
            }

            String sourceName = sourceGame.steamGameDataResponseDto().name();
            String sourceDescription = sourceGame.steamGameDataResponseDto().shortDescription();
            if (sourceDescription == null || sourceDescription.isEmpty()) {
                sourceDescription = sourceGame.steamGameDataResponseDto().detailedDescription();
            }
            List<String> sourceGenres = getGenres(sourceGame);

            log.info("Поиск похожих для игры: {}, жанры: {}", sourceName, sourceGenres);

            List<SteamAppResponse> similarGames = findSimilarGames(
                    sourceName,
                    sourceDescription,
                    sourceGenres,
                    appId,
                    finalLimit);

            responseObserver.onNext(mapper.toSimilarGamesResponse(similarGames));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при поиске похожих игр: {}", e.getMessage());
            responseObserver.onError(e);
        }
    }

    private List<String> getGenres(SteamGameDetailsResponseDto sourceGame) {
        if (sourceGame == null || !sourceGame.success()
                || sourceGame.steamGameDataResponseDto() == null) {
            return List.of();
        }

        var data = sourceGame.steamGameDataResponseDto();

        if (data.genres() == null) {
            return List.of();
        }

        return data.genres()
                .stream()
                .map(SteamGameDetailsResponseDto.SteamGenreResponseDto::description)
                .toList();
    }

    private List<SteamAppResponse> findSimilarGames(String sourceName,
                                                    String sourceDescription,
                                                    List<String> sourceGenres,
                                                    int sourceAppId,
                                                    int finalLimit) {

        //TODO: Реализовать поиск по схожести названия
        List<SteamAppResponse> byNameSimilar = List.of();

        //TODO: Реализовать поиск по схожести описания
        List<SteamAppResponse> byDescriptionSimilar = List.of();

        //TODO: Реализовать поиск по одинаковым жанрам
        List<SteamAppResponse> byGenresSimilar = List.of();

        return Stream.concat(
                        Stream.concat(byNameSimilar.stream(),
                                byDescriptionSimilar.stream()),
                        byGenresSimilar.stream())
                .filter(game -> game.getAppId() != sourceAppId)
                .distinct()
                .limit(finalLimit)
                .toList();
    }

    @Override
    public void searchGames(SearchGamesRequest request,
                            StreamObserver<SearchGamesResponse> responseObserver) {

        String query = request.getQuery();
        int limit = request.getLimit();
        String requestId = GrpcRequestIdServerInterceptor.REQUEST_ID_CONTEXT.get();

        int finalLimit = (limit > 0 && limit <= 100) ? limit : topByPlaytimeListSize;

        log.info("Получен запрос {} на поиск игр по запросу '{}' с лимитом {}", requestId, query, finalLimit);

        gameService.getAllGames()
                .flatMap(allGamesMap -> {
                    List<String> matchingNames = allGamesMap
                            .keySet()
                            .stream()
                            .filter(name -> name.toLowerCase().contains(query.toLowerCase()))
                            .limit(finalLimit)
                            .toList();
                    return gameService.findGames(Flux.fromIterable(matchingNames));
                })
                .flatMapMany(gameMap -> Flux.fromIterable(gameMap.entrySet()))
                .flatMap(entry -> steamStoreClient
                        .fetchGameDetails(String.valueOf(entry.getValue()))
                        .map(mapper::toSteamAppResponse)
                        .defaultIfEmpty(mapper.toSteamAppResponse(entry.getValue(), entry.getKey())))
                .collectList()
                .contextWrite(ctx -> ctx.put("requestId", requestId)
                        .put("requestIdLoggingParam", requestId))
                .blockOptional(Duration.ofSeconds(3))
                .ifPresentOrElse(games -> {
                            SearchGamesResponse response = SearchGamesResponse.newBuilder()
                                    .addAllGames(games)
                                    .build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND))
                );

    }

}
