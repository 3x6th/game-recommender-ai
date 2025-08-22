package ru.perevalov.gamerecommenderai.config;

import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.perevalov.gamerecommenderai.core.bucket.BucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.core.bucket.GuestUserBucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.core.bucket.SimpleUserBucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Order(2)
@Configuration
@RequiredArgsConstructor
public class BucketConfig {

    private final GuestUserBucketCacheKeyConstructor guestUserBucketCacheKeyConstructor;
    private final SimpleUserBucketCacheKeyConstructor simpleUserBucketCacheKeyConstructor;

    @Bean
    public Map<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors() {
        EnumMap<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors = new EnumMap<>(UserRole.class);
        bucketCacheKeyConstructors.put(UserRole.GUEST, guestUserBucketCacheKeyConstructor);
        bucketCacheKeyConstructors.put(UserRole.USER, simpleUserBucketCacheKeyConstructor);
        return bucketCacheKeyConstructors;
    }

}
