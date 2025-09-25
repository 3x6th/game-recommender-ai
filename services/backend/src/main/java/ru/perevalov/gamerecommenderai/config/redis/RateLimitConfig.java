package ru.perevalov.gamerecommenderai.config.redis;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Класс конфигурации для настройки распределенного ограничения запросов с использованием Bucket4j и Redis.
 * <p>
 * Этот класс настраивает механизм rate limiting, который использует Redis для хранения состояния
 * ограничений, что позволяет работать в распределенной среде с несколькими экземплярами приложения.
 * </p>
 *
 * <p>Конфигурация создает:
 * <ul>
 *   <li>{@link LettuceBasedProxyManager} - менеджер для распределенных корзин</li>
 *   <li>{@link Bucket} - корзину токенов для ограничения запросов</li>
 * </ul>
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RedisProps redisProps;

    /**
     * Создает менеджер для управления распределенными корзинами ограничений в Redis.
     * <p>
     * Использует CAS-based builder для обеспечения атомарности операций с корзинами.
     * Настраивает автоматическое удаление корзин через 10 минут неактивности.
     * </p>
     *
     * @param connection Redis подключение для работы с данными
     * @return менеджер распределенных корзин
     */
    @Bean
    public LettuceBasedProxyManager<byte[]> proxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(redisProps.bucketTtl()))
                .build();
    }

    /**
     * Создает распределенную корзину токенов для ограничения запросов к Steam API.
     * <p>
     * Корзина настроена с параметрами из {@link RedisProps} и использует Redis
     * для хранения состояния, обеспечивая согласованность между экземплярами приложения.
     * </p>
     *
     * @param proxyManager менеджер для создания распределенных корзин
     * @return корзина токенов для ограничения запросов
     */
    @Bean
    public Bucket apiBucket(LettuceBasedProxyManager<byte[]> proxyManager) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(redisProps.capacity())
                        .refillGreedy(
                                redisProps.tokensPerRefill(),
                                redisProps.refillDuration()
                        ))
                .build();

        return proxyManager.builder()
                .build(
                        redisProps.bucketKey().getBytes(StandardCharsets.UTF_8),
                        configSupplier
                );
    }
}
