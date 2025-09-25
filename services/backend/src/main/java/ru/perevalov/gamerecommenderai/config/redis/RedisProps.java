package ru.perevalov.gamerecommenderai.config.redis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Конфигурационные свойства для подключения к Redis и настройки ограничения запросов.
 * <p>
 * Этот record определяет все параметры конфигурации Redis, загружаемые из
 * свойств приложения с префиксом {@code redis}. Включает ограничения валидации
 * для обеспечения правильной конфигурации обязательных свойств.
 * </p>
 *
 * <p>Пример использования в {@code application.properties}:
 * <pre>{@code
 * redis.redis-uri=redis://localhost:6379
 * redis.capacity=100
 * redis.tokens-per-refill=10
 * redis.refill-duration=PT1S
 * redis.bucket-key=steam-api-global-bucket
 * }</pre>
 * </p>
 *
 * @param redisUri        URI Redis сервера (например, redis://localhost:6379)
 * @param capacity        максимальное количество токенов в корзине ограничений
 * @param tokensPerRefill количество токенов, добавляемых за интервал пополнения
 * @param refillDuration  временной интервал между пополнениями токенов
 * @param bucketKey       уникальный идентификатор корзины ограничений в Redis
 * @param bucketTtl       время жизни корзины в Redis при отсутствии активности
 */
@ConfigurationProperties(prefix = "redis")
@Validated
public record RedisProps(
        @NotBlank String redisUri,
        @Min(1) int capacity,
        @Min(1) int tokensPerRefill,
        @NotNull Duration refillDuration,
        @NotBlank String bucketKey,
        @NotNull Duration bucketTtl
) {
}
