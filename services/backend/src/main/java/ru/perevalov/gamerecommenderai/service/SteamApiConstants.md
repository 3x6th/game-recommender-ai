# SteamApiConstants - Enum для констант Steam API

## Описание

`SteamApiConstants` - это enum, который содержит все константы, используемые в `SteamApiService` для аннотаций Spring Boot.

## Структура

### Enum значения
- `STEAM_API_CIRCUIT_BREAKER` - имя для Circuit Breaker
- `STEAM_API_RETRY` - имя для Retry
- `STEAM_API_TIME_LIMITER` - имя для Time Limiter
- `STEAM_DATA_CACHE` - имя кэша
- `USER_GAMES_CACHE_KEY_PREFIX` - префикс ключа кэша для игр пользователя
- `GAME_RECOMMENDATIONS_CACHE_KEY_PREFIX` - префикс ключа кэша для рекомендаций

### Статические константы
Для использования в аннотациях добавлены статические поля:
- `STEAM_API_CIRCUIT_BREAKER_VALUE`
- `STEAM_API_RETRY_VALUE`
- `STEAM_API_TIME_LIMITER_VALUE`
- `STEAM_DATA_CACHE_VALUE`

## Использование в SteamApiService

```java
@CircuitBreaker(name = SteamApiConstants.STEAM_API_CIRCUIT_BREAKER_VALUE, fallbackMethod = "steamApiFallback")
@Retry(name = SteamApiConstants.STEAM_API_RETRY_VALUE, fallbackMethod = "steamApiFallback")
@TimeLimiter(name = SteamApiConstants.STEAM_API_TIME_LIMITER_VALUE, fallbackMethod = "steamApiFallback")
@Cacheable(value = SteamApiConstants.STEAM_DATA_CACHE_VALUE, key = "#gameId")
```

## Преимущества

1. **Централизация** - все константы в одном месте
2. **Типобезопасность** - enum обеспечивает проверку типов
3. **Рефакторинг** - легко изменить значения во всех местах
4. **Читаемость** - понятные имена констант
5. **Совместимость с аннотациями** - статические поля можно использовать в аннотациях

## Изменения

Все строковые литералы в аннотациях `SteamApiService` заменены на константы из enum:
- `"steamApi"` → `SteamApiConstants.STEAM_API_CIRCUIT_BREAKER_VALUE`
- `"steamData"` → `SteamApiConstants.STEAM_DATA_CACHE_VALUE`
