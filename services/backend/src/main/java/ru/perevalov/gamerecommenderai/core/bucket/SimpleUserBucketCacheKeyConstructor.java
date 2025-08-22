package ru.perevalov.gamerecommenderai.core.bucket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleUserBucketCacheKeyConstructor implements BucketCacheKeyConstructor {

    private final BucketUtil bucketUtil;

    @Override
    public String construct() {
        return bucketUtil.getUserRole() +
                ":" +
                bucketUtil.getUsername();
    }
}
