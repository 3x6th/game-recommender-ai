package ru.perevalov.gamerecommenderai.service;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamApiClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.mapper.SteamAppMapper;
import ru.perevalov.gamerecommenderai.repository.SteamAppRepository;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final SteamApiClient steamApiClient;
    private final SteamAppMapper steamAppMapper;
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final SteamAppRepository steamAppRepository;
    private final AsyncSaveService asyncSaveService;
    private Map<String, Long> appidToNameMap = new ConcurrentHashMap<>();

    @Value("${redis.cache.key}")
    private String cacheKey;

    /**
     * Retrieves all games: first checks Redis cache, then database, falls back to API fetch if both empty.
     */
    public Map<String, Long> getAllGames() {
        this.appidToNameMap = getAllGamesFromCache();
        if (!appidToNameMap.isEmpty()) {
            return this.appidToNameMap;
        }

        List<SteamAppEntity> gamesFromDb = steamAppRepository.findAll()
                // TODO: блокирующая заглушка. Переписать в PCAI-79
                .collectList().block();
        if (!gamesFromDb.isEmpty()) {
            SteamAppResponseDto dto = steamAppMapper.toResponseDto(gamesFromDb);
            appidToNameMap = steamAppMapper.toAppMap(dto);
            asyncSaveService.saveToCache(appidToNameMap);
            return appidToNameMap;
        }
        return fetchAndStoreGames();
    }

    /**
     * Finds specific games by names: searches Redis cache via HMGET {@link <a href="https://redis.io/commands/hmget">...</a>},
     * then DB fallback with case-insensitive LOWER match. Returns empty map if no matches.
     */
    public Map<String, Long> findGames(List<String> gameNames) {
        if (gameNames == null || gameNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Long> appidToNameMap = new LinkedHashMap<>();

        appidToNameMap.putAll(searchGamesInCache(gameNames));

        List<String> missingNamesLower = getMissingNamesLower(gameNames, appidToNameMap);

        appidToNameMap.putAll(searchMissingGamesInDatabase(missingNamesLower, gameNames));

        return appidToNameMap;
    }

    /**
     * Updates games by fetching from Steam API {@link <a href="https://developer.valvesoftware.com/wiki/Steam_Web_API#GetAppList">...</a>},
     * saves to cache and DB asynchronously, then clears in-memory map to free memory.
     */
    public void updateGames() {
        fetchAndStoreGames();
        appidToNameMap.clear();
        log.info("In-memory appidToNameMap cleared after update");
    }

    /**
     * Searches for games in Redis cache using HMGET {@link <a href="https://redis.io/commands/hmget">...</a>} for efficient multi-key retrieval.
     * Returns found name-appid pairs.
     */
    private Map<String, Long> searchGamesInCache(List<String> gameNames) {
        byte[][] fields = gameNames.stream()
                .map(name -> name.getBytes(StandardCharsets.UTF_8))
                .toArray(byte[][]::new);
        List<KeyValue<byte[], byte[]>> redisResults = redisConnection.sync().hmget(cacheKey.getBytes(StandardCharsets.UTF_8), fields);

        Map<String, Long> cachedGames = new LinkedHashMap<>();
        for (int i = 0; i < gameNames.size(); i++) {
            String name = gameNames.get(i);
            if (redisResults.get(i) != null && redisResults.get(i).hasValue()) {
                try {
                    Long appid = Long.parseLong(new String(redisResults.get(i).getValue(), StandardCharsets.UTF_8));
                    cachedGames.put(name, appid);
                } catch (NumberFormatException e) {
                    log.warn("Invalid appid value in cache for game '{}': {}", name, e.getMessage());
                }
            }
        }
        return cachedGames;
    }

    /**
     * Collects missing game names not found in cache, converts to lowercase for case-insensitive DB lookup using LOWER function
     * {@link <a href="https://www.postgresql.org/docs/current/functions-string.html#FUNCTIONS-STRING-OTHER">...</a>}.
     */
    private List<String> getMissingNamesLower(List<String> originalNames, Map<String, Long> foundInCache) {
        return originalNames.stream()
                .filter(name -> !foundInCache.containsKey(name))
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Queries DB for missing games using case-insensitive match via LOWER in JPQL
     * {@link <a href="https://docs.oracle.com/cd/E11035_01/kodo41/full/html/ejb3_langref.html#ejb3_langref_select_from">...</a>}.
     * Maps results back to original case.
     */
    private Map<String, Long> searchMissingGamesInDatabase(List<String> missingNamesLower, List<String> originalNames) {
        if (missingNamesLower.isEmpty()) {
            return Collections.emptyMap();
        }

        List<SteamAppEntity> gamesFromDb = steamAppRepository.findByLowerNameIn(missingNamesLower)
                // TODO: блокирующая заглушка. Переписать в PCAI-79
                .collectList().block();

        Map<String, Long> dbGames = new LinkedHashMap<>();
        gamesFromDb.forEach(entity -> originalNames.stream()
                .filter(name -> name.equalsIgnoreCase(entity.getName()))
                .findFirst()
                .ifPresent(name -> dbGames.put(name, entity.getAppid())));
        return dbGames;
    }

    /**
     * Fetches games from Steam API client {@link SteamApiClient;} and stores to cache/DB asynchronously .
     */
    private Map<String, Long> fetchAndStoreGames() {
        SteamAppResponseDto steamAppResponseDto = steamApiClient.fetchSteamApps();
        appidToNameMap = steamAppMapper.toAppMap(steamAppResponseDto);
        List<SteamAppEntity> gameEntities = steamAppMapper.toEntities(steamAppResponseDto.appList().apps());

        CompletableFuture<Void> cacheFuture = asyncSaveService.saveToCache(appidToNameMap);
        CompletableFuture<Void> dbFuture = asyncSaveService.saveToDatabase(gameEntities);

        try {
            CompletableFuture.allOf(cacheFuture, dbFuture).join();
            log.info("Both cache and database save operations completed");
        } catch (Exception e) {
            log.error("Error during async save operations", e);
            throw new GameRecommenderException(ErrorType.FETCH_STORE_GAMES_ERROR, e);
        }

        return appidToNameMap;
    }

    private Map<String, Long> getAllGamesFromCache() {
        try {
            Map<byte[], byte[]> redisMap = redisConnection.sync().hgetall(cacheKey.getBytes(StandardCharsets.UTF_8));
            if (!redisMap.isEmpty()) {
                return redisMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> new String(e.getKey(), StandardCharsets.UTF_8),
                                e -> Long.parseLong(new String(e.getValue(), StandardCharsets.UTF_8))
                        ));
            }
        } catch (Exception e) {
            log.warn("Failed to read from Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_READ_ERROR, e);
        }
        return Collections.emptyMap();
    }

}
