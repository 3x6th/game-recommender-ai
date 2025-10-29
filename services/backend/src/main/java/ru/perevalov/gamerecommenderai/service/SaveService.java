package ru.perevalov.gamerecommenderai.service;

import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.SteamAppRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SaveService {
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final SteamAppRepository steamAppRepository;
    @Value("${redis.cache.key}")
    private String cacheKey;
    @Value("${app.batch.size}")
    private int batchSize;
    @Value("${app.batch.concurrency:4}")
    private int maxConcurrency;

    public Mono<Void> saveToCache(Map<String, Long> appMap) {
        if (appMap == null || appMap.isEmpty()) {
            log.info("Save to Cache skipped: map is empty or null");
            return Mono.empty();
        }

        log.info("saveToCache started for {} apps", appMap.size());

        return Mono.fromCallable(() ->
                        appMap.entrySet().stream()
                                .collect(Collectors.toMap(
                                        e -> e.getKey().getBytes(StandardCharsets.UTF_8),
                                        e -> e.getValue().toString().getBytes(StandardCharsets.UTF_8)
                                ))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(redisMap ->
                        redisConnection.reactive().hset(cacheKey.getBytes(StandardCharsets.UTF_8), redisMap)
                                .then()
                                .doOnSuccess(v -> log.info("Steam apps saved to Redis hash"))
                )
                .onErrorResume(e ->
                        Mono.error(new GameRecommenderException(ErrorType.REDIS_CACHE_SAVE_ERROR, e))
                );
    }

    /**
     * Async saves a large list of Steam games to DB in batches.
     * Splits list into batches, executes each in parallel thread.
     * Uses UPSERT: updates existing by appid or inserts new.
     * Waits for all batch inserts to complete, then logs results (time, errors).
     * Waits until all futures finish before proceeding.
     *
     * @param games List of games to save
     * @return Mono<Void> completes when all batches finish
     */
    public Mono<Void> saveToDatabase(List<SteamAppEntity> games) {
        if (games.isEmpty()) {
            log.info("No games to save.");
            return Mono.empty();
        }

        long startTime = System.currentTimeMillis();

        return splitGamesIntoBatchesForInserting(games)
                .flatMap(monoBatch -> monoBatch, maxConcurrency)
                .then()
                .doFinally(signalType -> {
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    String statusMessage = switch (signalType) {
                        case ON_COMPLETE -> "completed successfully";
                        case ON_ERROR -> "FAILED due to error";
                        case CANCEL -> "was CANCELLED";
                        default -> "finished with unknown status";
                    };

                    log.info("Bulk insert finished for {} games. Status: {}. Total time: {} ms",
                            games.size(), statusMessage, duration);
                })
                .doOnError(e -> log.error("Failed to plan bulk insert for {} games", games.size(), e));

    }

    /**
     * Splits game list into batches and schedules async inserts for each.
     * Returns flux of mono for batch processing.
     *
     * @param games Full list to split
     * @return Flux of Mono for each batch
     */
    private Flux<Mono<Void>> splitGamesIntoBatchesForInserting(List<SteamAppEntity> games) {
        return Flux.fromIterable(games)
                .buffer(batchSize)
                .index()
                .map(tuple -> {
                    Long batchNumber = tuple.getT1() + 1;
                    List<SteamAppEntity> batch = tuple.getT2();

                    return executeBatchInsertAsync(batch, batchNumber);
                });
    }

    /**
     * Executes one batch insert asynchronously.
     * Includes thread logging, repository call, and batch error handling.
     *
     * @param batch       List for this batch
     * @param batchNumber Batch id for logs
     * @return Mono<Void> for async completion
     */
    private Mono<Void> executeBatchInsertAsync(List<SteamAppEntity> batch, Long batchNumber) {
        return steamAppRepository.batchInsert(batch)
                .doOnSuccess(v -> log.info("Batch {} execution completed in thread {}",
                        batchNumber, Thread.currentThread().getName()))
                .doOnError(e ->
                        log.error("Batch {} failed during execution: {}", batchNumber, e.getMessage(), e)
                );
    }
}