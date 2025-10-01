package ru.perevalov.gamerecommenderai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.perevalov.gamerecommenderai.client.SteamApiClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.mapper.SteamAppMapper;
import ru.perevalov.gamerecommenderai.repository.SteamAppRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final SteamApiClient steamApiClient;
    private final SteamAppMapper steamAppMapper;
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final ObjectMapper objectMapper;
    private final SteamAppRepository steamAppRepository;
    private Map<Long, String> appidToNameMap = new ConcurrentHashMap<>();

    private static final int BATCH_SIZE = 5000;
    private static final String CACHE_KEY = "steam_apps";

    public Map<Long, String> getGames() {
        Optional<SteamAppResponseDto> cached = getFromCache();
        if (cached.isPresent()) {
            appidToNameMap = steamAppMapper.toAppMap(cached.get());
            return appidToNameMap;
        }

        List<SteamAppEntity> gamesFromDb = steamAppRepository.findAll();
        if (!gamesFromDb.isEmpty()) {
            SteamAppResponseDto dto = steamAppMapper.toResponseDto(gamesFromDb);
            appidToNameMap = steamAppMapper.toAppMap(dto);
            saveToCache(appidToNameMap);
            return appidToNameMap;
        }
        return fetchAndStoreGames();
    }

    @Async
    public void updateSteamApps() {
        log.info("Scheduled async update started");
        fetchAndStoreGames();
        log.info("Scheduled async update finished");
    }

    private Map<Long, String> fetchAndStoreGames() {
        SteamAppResponseDto steamAppResponseDto = steamApiClient.fetchSteamApps();

        appidToNameMap = steamAppMapper.toAppMap(steamAppResponseDto);
        saveToCache(appidToNameMap);

        List<SteamAppEntity> gameEntities = steamAppMapper.toEntities(steamAppResponseDto.appList().apps());
        saveToDatabase(gameEntities);

        return appidToNameMap;
    }

    private void saveToDatabase(List<SteamAppEntity> games) {
        if (games.isEmpty()) return;

        int total = games.size();
        int batchNumber = 0;

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<SteamAppEntity> batchGames = games.subList(i, end);
            batchNumber++;
            log.info("Inserting batch {}: records {} - {}", batchNumber, i + 1, end);

            steamAppRepository.batchInsert(batchGames);
        }
        log.info("Saved {} apps to database", total);
    }

    private void saveToCache(Map<Long, String> appMap) {
        if (appMap == null || appMap.isEmpty()) {
            return;
        }
        try {
            redisConnection.sync().set(CACHE_KEY.getBytes(StandardCharsets.UTF_8), objectMapper.writeValueAsBytes(appMap));
            log.info("Steam apps saved to Redis cache");
        } catch (Exception e) {
            log.warn("Failed to save Steam apps to Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_SAVE_ERROR, e);
        }
    }

    private Optional<SteamAppResponseDto> getFromCache() {
        try {
            byte[] data = redisConnection.sync().get(CACHE_KEY.getBytes(StandardCharsets.UTF_8));
            if (data != null) {
                return Optional.of(objectMapper.readValue(data, SteamAppResponseDto.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read from Redis cache", e);
            throw new GameRecommenderException(ErrorType.REDIS_CACHE_READ_ERROR, e);
        }
        return Optional.empty();
    }

}
