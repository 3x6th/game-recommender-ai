package ru.perevalov.gamerecommenderai.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.core.BucketCacheKeyConstructor;
import ru.perevalov.gamerecommenderai.security.UserRole;

import java.util.Map;

@Component("bucketUtil")
@RequiredArgsConstructor
public class BucketUtil {

    private final JwtUtil jwtUtil;
    private final Map<UserRole, BucketCacheKeyConstructor> bucketCacheKeyConstructors;

    /**
     * Генерирует и возвращает ключ кэширования баккета по ролям.
     *
     * @param request объект {@link HttpServletRequest}
     * @return ключ кэширования для баккета
     */
    public String getBucketCacheKey(HttpServletRequest request) {
        DecodedJWT jwt = jwtUtil.decodeJwtToken(request);
        UserRole byRole = jwtUtil.roleClaim(jwt);

        return bucketCacheKeyConstructors.get(byRole)
                .construct(request);
    }

}
