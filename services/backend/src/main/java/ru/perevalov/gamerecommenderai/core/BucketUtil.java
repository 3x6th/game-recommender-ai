package ru.perevalov.gamerecommenderai.core;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

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
        String bearerToken = jwtUtil.extractToken(request);
        DecodedJWT jwt = jwtUtil.decodeToken(bearerToken);
        UserRole byRole = jwtUtil.roleClaim(jwt);

        return bucketCacheKeyConstructors.get(byRole)
                .construct(request);
    }

}
