package ru.perevalov.gamerecommenderai.core;

import jakarta.servlet.http.HttpServletRequest;

public interface BucketCacheKeyConstructor {

    String construct(HttpServletRequest request);

}
