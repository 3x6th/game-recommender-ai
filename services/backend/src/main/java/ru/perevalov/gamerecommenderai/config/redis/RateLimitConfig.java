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
import java.time.Duration;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RedisProps redisProps;

    @Bean
    public LettuceBasedProxyManager<byte[]> proxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

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
