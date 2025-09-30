package ru.perevalov.gamerecommenderai.service;

import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.SteamAppRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncSaveService {
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final SteamAppRepository steamAppRepository;
    private Executor executor;
    @Value("${redis.cache.key}")
    private String cacheKey;
    @Value("${app.batch.size}")
    private int batchSize;
    @Value("${app.batch.thread-pool.size:4}")
    private int threadPoolSize;

    @PostConstruct
    public void init() {
        log.info("Initializing ThreadPoolExecutor with {} threads", threadPoolSize);
        executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Async
    public CompletableFuture<Void> saveToCache(Map<String, Long> appMap) {
        log.info("saveToCache started in thread: {}", Thread.currentThread().getName());
        if (appMap == null || appMap.isEmpty()) return CompletableFuture.completedFuture(null);

        try {
            Map<byte[], byte[]> redisMap = appMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().getBytes(StandardCharsets.UTF_8),
                            e -> e.getValue().toString().getBytes(StandardCharsets.UTF_8)
                    ));
            redisConnection.sync().hset(cacheKey.getBytes(StandardCharsets.UTF_8), redisMap);
            log.info("Steam apps saved to Redis hash");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Failed to save Steam apps to Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_SAVE_ERROR, e);
        }
    }

    /**
     * Async saves a large list of Steam games to DB in batches.
     * Splits list into batches, executes each in parallel thread.
     * Uses UPSERT: updates existing by appid or inserts new.
     *
     * @param games List of games to save
     * @return CompletableFuture<Void> completes when all batches finish
     */
    @Async
    public CompletableFuture<Void> saveToDatabase(List<SteamAppEntity> games) {
        if (games.isEmpty()) {
            log.info("No games to save.");
            return CompletableFuture.completedFuture(null);
        }

        log.info("Starting bulk insert of {} games with batch size={}, thread pool size={} (expect ~{} batches per thread)",
                games.size(), batchSize, threadPoolSize, (int) Math.ceil((double) games.size() / batchSize / threadPoolSize));

        long startTime = System.currentTimeMillis();

        try {
            List<CompletableFuture<Void>> batchFutures = splitGamesIntoBatchesForInserting(games);
            int totalGames = games.size();

            return waitForBulkInsertCompletion(batchFutures, totalGames, startTime);
        } catch (Exception e) {
            log.error("Failed to plan bulk insert for {} games", games.size(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Splits game list into batches and schedules async inserts for each.
     * Returns list of futures for batch processing.
     *
     * @param games Full list to split
     * @return List of CompletableFuture for each batch
     */
    private List<CompletableFuture<Void>> splitGamesIntoBatchesForInserting(List<SteamAppEntity> games) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int total = games.size();
        int batchNumber = 0;

        for (int i = 0; i < total; i += batchSize) {
            int endIdx = Math.min(i + batchSize, total);
            List<SteamAppEntity> batch = games.subList(i, endIdx);
            final int currentBatchNumber = ++batchNumber;

            CompletableFuture<Void> future = executeBatchInsertAsync(batch, currentBatchNumber);
            futures.add(future);
        }

        return futures;
    }

    /**
     * Executes one batch insert asynchronously in thread pool.
     * Includes thread logging, repository call, and batch error handling.
     *
     * @param batch       List for this batch
     * @param batchNumber Batch id for logs
     * @return CompletableFuture<Void> for async completion
     */
    private CompletableFuture<Void> executeBatchInsertAsync(List<SteamAppEntity> batch, int batchNumber) {
        return CompletableFuture.runAsync(() -> {
            log.info("Executing batch {} in thread {}", batchNumber, Thread.currentThread().getName());
            steamAppRepository.batchInsert(batch);  // Calls upsert in repo
            log.info("Batch {} execution completed", batchNumber);
        }, executor).exceptionally(ex -> {
            log.error("Batch {} failed during execution: {}", batchNumber, ex.getMessage(), ex);
            throw new CompletionException(ex);
        });
    }

    /**
     * Waits for all batch inserts to complete, then logs results (time, errors).
     * Waits until all futures finish before proceeding.
     *
     * @param futures    List of batch futures
     * @param totalGames Total records processed
     * @param startTime  Start time for total duration calc
     * @return CompletableFuture<Void> for final completion
     */
    private CompletableFuture<Void> waitForBulkInsertCompletion(List<CompletableFuture<Void>> futures, int totalGames, long startTime) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    long endTime = System.currentTimeMillis();
                    log.info("Bulk insert completed for {} games. Total time: {} ms", totalGames, endTime - startTime);
                    if (throwable != null) {
                        log.error("Some batches failed in bulk insert: {}", throwable.getMessage(), throwable);
                    }
                });
    }
}