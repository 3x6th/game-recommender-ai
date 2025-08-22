package ru.perevalov.gamerecommenderai.core;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.service.JwtUtil;

@Component
@RequiredArgsConstructor
public class SimpleUserBucketCacheKeyConstructor implements BucketCacheKeyConstructor {

    private final JwtUtil jwtUtil;

    @Override
    public String construct(HttpServletRequest request) {
        DecodedJWT jwt = jwtUtil.decodeJwtToken(request);
        return jwtUtil.roleClaim(jwt) +
                ":" +
                jwt.getSubject();
    }
}
