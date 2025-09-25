package ru.perevalov.gamerecommenderai.config.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Класс конфигурации для настройки Redis клиента и подключения.
 * <p>
 * Этот класс предоставляет Spring бины для управления Redis клиентом и подключением
 * с использованием Lettuce клиента. Обеспечивает правильную очистку ресурсов через
 * методы уничтожения для предотвращения утечек подключений.
 * </p>
 *
 * <p>Конфигурация создает:
 * <ul>
 *   <li>{@link RedisClient} - основной экземпляр Redis клиента</li>
 *   <li>{@link StatefulRedisConnection} - постоянное подключение к Redis</li>
 * </ul>
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProps redisProps;

    /**
     * Создает и настраивает экземпляр Redis клиента.
     * <p>
     * Клиент настроен на автоматическое отключение при закрытии контекста Spring,
     * что обеспечивает правильную очистку ресурсов.
     * </p>
     *
     * @return настроенный экземпляр Redis клиента
     * @throws IllegalArgumentException если URI Redis невалиден
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        return RedisClient.create(redisProps.redisUri());
    }

    /**
     * Создает постоянное подключение к Redis с использованием кодека для байтовых массивов.
     * <p>
     * Подключение использует {@link ByteArrayCodec} для эффективной работы с бинарными данными.
     * Подключение автоматически закрывается при завершении работы приложения.
     * </p>
     *
     * @param redisClient Redis клиент для установки подключения
     * @return постоянное подключение к Redis
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> redisConnection(RedisClient redisClient) {
        return redisClient.connect(new ByteArrayCodec());
    }
}
