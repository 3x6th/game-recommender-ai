package ru.perevalov.gamerecommenderai.config.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProps redisProps;

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        return RedisClient.create(redisProps.redisUri());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> redisConnection(RedisClient redisClient) {
        return redisClient.connect(new ByteArrayCodec());
    }
}
