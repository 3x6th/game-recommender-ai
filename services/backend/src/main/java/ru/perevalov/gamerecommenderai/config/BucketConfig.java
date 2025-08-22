package ru.perevalov.gamerecommenderai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import ru.perevalov.gamerecommenderai.core.BucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.core.GuestUserBucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.core.SimpleUserBucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.security.UserRole;

import java.util.EnumMap;
import java.util.Map;

@Order(2)
@Configuration
@RequiredArgsConstructor
@EnableCaching
@EnableAspectJAutoProxy
class BucketConfig {

    private final GuestUserBucketCacheKeyConstructor guestUserBucketCacheKeyConstructor;
    private final SimpleUserBucketCacheKeyConstructor simpleUserBucketCacheKeyConstructor;

    @Bean
    public Map<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors() {
        EnumMap<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors = new EnumMap<>(UserRole.class);
        bucketCacheKeyConstructors.put(UserRole.GUEST_USER, guestUserBucketCacheKeyConstructor);
        bucketCacheKeyConstructors.put(UserRole.SIMPLE_USER, simpleUserBucketCacheKeyConstructor);
        return bucketCacheKeyConstructors;
    }

}
