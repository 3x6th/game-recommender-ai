package ru.perevalov.gamerecommenderai.service;

import io.lettuce.core.api.StatefulRedisConnection;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncSaveService {
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final SteamAppRepository steamAppRepository;
    @Value("${redis.cache.key}")
    private String cacheKey;
    @Value("${redis.batch.size}")
    private int batchSize;

    @Async
    public CompletableFuture<Void> saveToCache(Map<Long, String> appMap) {
        log.info("saveToCache started in thread: {}", Thread.currentThread().getName());
        if (appMap == null || appMap.isEmpty()) return CompletableFuture.completedFuture(null);

        try {
            Map<byte[], byte[]> redisMap = appMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().toString().getBytes(StandardCharsets.UTF_8),
                            e -> e.getValue().getBytes(StandardCharsets.UTF_8)
                    ));
            redisConnection.sync().hset(cacheKey.getBytes(StandardCharsets.UTF_8), redisMap);
            log.info("Steam apps saved to Redis hash");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Failed to save Steam apps to Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_SAVE_ERROR, e);
        }
    }

    @Async
    public CompletableFuture<Void> saveToDatabase(List<SteamAppEntity> games) {
        log.info("saveToDatabase started in thread: {}", Thread.currentThread().getName());
        if (games.isEmpty()) return CompletableFuture.completedFuture(null);
        try {
            int total = games.size();
            int batchNumber = 0;
            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<SteamAppEntity> batchGames = games.subList(i, end);
                batchNumber++;
                log.info("Inserting batch {}: records {} - {}", batchNumber, i + 1, end);
                steamAppRepository.batchInsert(batchGames);
            }
            log.info("Saved {} apps to database", total);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to save Steam apps to database", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}