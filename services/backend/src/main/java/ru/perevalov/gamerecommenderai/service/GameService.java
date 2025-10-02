package ru.perevalov.gamerecommenderai.service;

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
    private Map<Long, String> appidToNameMap = new ConcurrentHashMap<>();

    @Value("${redis.cache.key}")
    private String cacheKey;

    public Map<Long, String> getGames() {
        appidToNameMap = getFromCache();
        if (!appidToNameMap.isEmpty()) {
            return appidToNameMap;
        }

        List<SteamAppEntity> gamesFromDb = steamAppRepository.findAll();
        if (!gamesFromDb.isEmpty()) {
            SteamAppResponseDto dto = steamAppMapper.toResponseDto(gamesFromDb);
            appidToNameMap = steamAppMapper.toAppMap(dto);
            asyncSaveService.saveToCache(appidToNameMap);
            return appidToNameMap;
        }
        return fetchAndStoreGames();
    }

    public void updateGames() {
        fetchAndStoreGames();
    }

    private Map<Long, String> fetchAndStoreGames() {
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

    private Map<Long, String> getFromCache() {
        try {
            Map<byte[], byte[]> redisMap = redisConnection.sync().hgetall(cacheKey.getBytes(StandardCharsets.UTF_8));
            if (!redisMap.isEmpty()) {
                return redisMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> Long.parseLong(new String(e.getKey(), StandardCharsets.UTF_8)),
                                e -> new String(e.getValue(), StandardCharsets.UTF_8)
                        ));
            }
        } catch (Exception e) {
            log.warn("Failed to read from Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_READ_ERROR, e);
        }
        return Collections.emptyMap();
    }

}
