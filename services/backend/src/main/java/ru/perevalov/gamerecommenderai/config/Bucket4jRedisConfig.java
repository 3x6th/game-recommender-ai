package ru.perevalov.gamerecommenderai.config;

import com.giffing.bucket4j.spring.boot.starter.config.cache.AbstractCacheResolverTemplate;
import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AbstractProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import ru.perevalov.gamerecommenderai.config.redis.RedisProps;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Конфигурация Bucket4j rate limiting с Redis.
 */
@Configuration
@RequiredArgsConstructor
public class Bucket4jRedisConfig {

    private final RedisProps redisProps;

    /**
     * Создает кэш-резолвер для Bucket4j.
     *
     * @param redisClient Redis клиент
     * @return кэш-резолвер для rate limiting
     */
    @Bean
    public SyncCacheResolver bucket4jRedisResolver(RedisClient redisClient) {
        return new LettuceCacheResolver(redisClient, redisProps.bucketTtl());
    }

    /**
     * Кэш-резолвер для Bucket4j с Redis.
     */
    private static class LettuceCacheResolver extends AbstractCacheResolverTemplate<byte[]> implements SyncCacheResolver {

        private final RedisClient redisClient;
        private final Duration bucketTtl;

        /**
         * Конструктор.
         *
         * @param redisClient Redis клиент
         * @param bucketTtl   Время жизни корзины
         */
        public LettuceCacheResolver(RedisClient redisClient, Duration bucketTtl) {
            this.redisClient = redisClient;
            this.bucketTtl = bucketTtl;
        }

        /**
         * Возвращает false для синхронного режима работы.
         *
         * @return false
         */
        @Override
        public boolean isAsync() {
            return false;
        }

        /**
         * Создает прокси-менеджер для Redis.
         *
         * @param cacheName имя кэша
         * @return прокси-менеджер
         */
        @Override
        public AbstractProxyManager<byte[]> getProxyManager(String cacheName) {
            return Bucket4jLettuce.casBasedBuilder(redisClient)
                    .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(bucketTtl))
                    .build();
        }

        /**
         * Конвертирует ключ в байты.
         *
         * @param key строковый ключ
         * @return ключ в виде байтов
         */
        @Override
        public byte[] castStringToCacheKey(String key) {
            return key.getBytes(UTF_8);
        }
    }
}
