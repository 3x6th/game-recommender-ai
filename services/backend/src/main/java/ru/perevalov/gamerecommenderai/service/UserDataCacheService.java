package ru.perevalov.gamerecommenderai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataCacheService {

    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final ObjectMapper objectMapper;

    @Value("${redis.cache.steam-profiles.key:steam_profiles}")
    private String steamProfilesCacheKey;

    @Value("${redis.cache.user-game-stats.key:user_game_stats}")
    private String userGameStatsCacheKey;

    public Mono<Void> saveSteamProfile(Long steamId, SteamProfile profile) {
        if (steamId == null || profile == null) {
            return Mono.empty();
        }
        return writeHashValue(steamProfilesCacheKey, steamId.toString(), profile);
    }

    public Mono<Void> saveUserGameStats(Long steamId, UserGameStats stats) {
        if (steamId == null || stats == null) {
            return Mono.empty();
        }
        return writeHashValue(userGameStatsCacheKey, steamId.toString(), stats);
    }

    private Mono<Void> writeHashValue(String hashKey, String fieldKey, Object value) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(value))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> redisConnection
                        .reactive()
                        .hset(
                                hashKey.getBytes(StandardCharsets.UTF_8),
                                fieldKey.getBytes(StandardCharsets.UTF_8),
                                bytes
                        )
                        .then()
                )
                .doOnSuccess(v -> log.debug("Cached user data in Redis hash={}, field={}", hashKey, fieldKey))
                .onErrorMap(JsonProcessingException.class,
                        e -> new GameRecommenderException(ErrorType.REDIS_USER_DATA_CACHE_SAVE_ERROR, e))
                .onErrorMap(e -> !(e instanceof GameRecommenderException),
                        e -> new GameRecommenderException(ErrorType.REDIS_USER_DATA_CACHE_SAVE_ERROR, e));
    }
}

