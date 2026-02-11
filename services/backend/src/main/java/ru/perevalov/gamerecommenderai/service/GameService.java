package ru.perevalov.gamerecommenderai.service;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final SteamApiClient steamApiClient;
    private final SteamAppMapper steamAppMapper;
    private final StatefulRedisConnection<byte[], byte[]> redisConnection;
    private final SteamAppRepository steamAppRepository;
    private final SaveService saveService;

    @Value("${redis.cache.key}")
    private String cacheKey;

    /**
     * Извлекает все игры: сначала проверяет кэш Redis, затем базу данных, возвращается к выборке из API, если они оба пусты.
     * Использует оператор publishOn для передачи сложного маппинга в отдельный пул потоков
     * и предотвращения блокировки Event Loop потоков
     */
    public Mono<Map<String, Long>> getAllGames() {
        return getAllGamesFromCache()
                .flatMap(cacheMap -> {
                    if (!cacheMap.isEmpty()) {
                        return Mono.just(cacheMap);
                    }
                    return steamAppRepository.findAll()
                                             .collectList()
                                             .publishOn(Schedulers.boundedElastic())
                                             .flatMap(dbList -> {
                                                 if (dbList.isEmpty()) {
                                                     return Mono.empty();
                                                 }

                                                 SteamAppResponseDto dto = steamAppMapper.toResponseDto(dbList);
                                                 Map<String, Long> appMap = steamAppMapper.toAppMap(dto);

                                                 return saveService.saveToCache(appMap)
                                                                   .thenReturn(appMap);
                                             })
                                             .switchIfEmpty(fetchAndStoreGames());
                });
    }

    /**
     * Finds specific games by names: searches Redis cache via HMGET {@link <a href="https://redis.io/commands/hmget">...</a>},
     * then DB fallback with case-insensitive LOWER match. Returns empty map if no matches.
     */
    public Mono<Map<String, Long>> findGames(Flux<String> gameNames) {
        return gameNames
                .collectList()
                .flatMap(gameList -> {
                    if (gameList.isEmpty()) {
                        return Mono.just(Collections.emptyMap());
                    }

                    return searchGamesInCache(gameList)
                            .flatMap(cachedGames -> {
                                List<String> missingNamesLower = getMissingNamesLower(gameList, cachedGames);

                                if (missingNamesLower.isEmpty()) {
                                    return Mono.just(cachedGames);
                                }

                                return searchMissingGamesInDatabase(missingNamesLower, gameList)
                                        .map(dbMap -> {
                                            Map<String, Long> appidToNameMap = new LinkedHashMap<>(cachedGames);
                                            appidToNameMap.putAll(dbMap);
                                            return appidToNameMap;
                                        });
                            });
                });
    }

    /**
     * Updates games by fetching from Steam API {@link <a href="https://developer.valvesoftware.com/wiki/Steam_Web_API#GetAppList">...</a>},
     * saves to cache and DB asynchronously, then clears in-memory map to free memory.
     */
    public Mono<Void> updateGames() {
        return fetchAndStoreGames()
                .then();
    }

    /**
     * Searches for games in Redis cache using HMGET {@link <a href="https://redis.io/commands/hmget">...</a>} for efficient multi-key retrieval.
     * Returns found name-appid pairs.
     */
    private Mono<Map<String, Long>> searchGamesInCache(List<String> gameNames) {
        byte[][] fields = gameNames.stream()
                                   .map(name -> name.getBytes(StandardCharsets.UTF_8))
                                   .toArray(byte[][]::new);
        return redisConnection.reactive().hmget(cacheKey.getBytes(StandardCharsets.UTF_8), fields)
                              .collectList()
                              .map(redisResults -> {
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
                              });
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
    private Mono<Map<String, Long>> searchMissingGamesInDatabase(List<String> missingNamesLower, List<String> originalNames) {
        if (missingNamesLower.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return steamAppRepository.findByLowerNameIn(missingNamesLower)
                                 .collectList()
                                 .map(gamesFromDb -> {
                                     Map<String, Long> dbGames = new LinkedHashMap<>();

                                     gamesFromDb.forEach(entity -> originalNames.stream()
                                                                                .filter(name -> name.equalsIgnoreCase(entity.getName()))
                                                                                .findFirst()
                                                                                .ifPresent(name -> dbGames.put(name, entity.getAppid())));

                                     return dbGames;
                                 });
    }

    /**
     * Извлекает игры из Steam API client {@link SteamApiClient} и сохраняет в кэше/базе данных асинхронно.
     * Использует оператор publishOn для передачи интенсивного маппинга в отдельный пул потоков
     * и предотвращения блокировки Event Loop потоков
     */
    private Mono<Map<String, Long>> fetchAndStoreGames() {
        return steamApiClient.fetchSteamApps()
                             .publishOn(Schedulers.boundedElastic())
                             .flatMap(steamAppResponseDto -> {

                                 List<SteamAppEntity> gameEntities = steamAppMapper.toEntities(steamAppResponseDto.appList().apps());
                                 Map<String, Long> appMap = steamAppMapper.toAppMap(steamAppResponseDto);

                                 Mono<Void> cache = saveService.saveToCache(appMap);
                                 Mono<Void> db = saveService.saveToDatabase(gameEntities);

                                 return Mono.when(cache, db)
                                            .doOnSuccess(v -> log.info("Both cache and database save operations completed"))
                                            .doOnError(e -> log.error("Error during async save operations", e)
                                            )
                                            .onErrorResume(e -> Mono.error(new GameRecommenderException(ErrorType.FETCH_STORE_GAMES_ERROR, e))
                                            )
                                            .thenReturn(appMap);
                             });
    }

    /**
     * Извлекает игры из кэша.
     * Использует оператор publishOn для передачи интенсивного парсинга в отдельный пул потоков
     * и предотвращения блокировки Event Loop потоков
     */
    private Mono<Map<String, Long>> getAllGamesFromCache() {
        return redisConnection.reactive().hgetall(cacheKey.getBytes(StandardCharsets.UTF_8))
                              .collectList()
                              .publishOn(Schedulers.boundedElastic())
                              .flatMap(redisResults -> {
                                  Map<String, Long> result = new LinkedHashMap<>();
                                  for (KeyValue<byte[], byte[]> kv : redisResults) {
                                      try {
                                          String key = new String(kv.getKey(), StandardCharsets.UTF_8);
                                          Long value = Long.parseLong(new String(kv.getValue(), StandardCharsets.UTF_8));
                                          result.put(key, value);
                                      } catch (Exception e) {
                                          log.warn("Failed to parse cache entry: {}", e.getMessage());
                                      }
                                  }
                                  return Mono.just(result);
                              })
                              .onErrorResume(e -> Mono.error(new GameRecommenderException(ErrorType.REDIS_CACHE_READ_ERROR, e))
                              );
    }

}