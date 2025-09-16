package ru.perevalov.gamerecommenderai.core.bucket;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Component("requestOnBucketRouter")
@RequiredArgsConstructor
public class RequestOnBucketRouter {

    private final Map<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors;
    private final BucketUtil bucketUtil;

    /**
     * Генерирует и возвращает ключ кэширования баккета по ролям.
     *
     * @return ключ кэширования для баккета
     */
    public String getBucketCacheKey() {
        return bucketCacheKeyConstructors
                       .get(byRole())
                       .construct();
    }

    private UserRole byRole() {
        return UserRole.valueOf(
                bucketUtil.getUserRole());
    }

}
