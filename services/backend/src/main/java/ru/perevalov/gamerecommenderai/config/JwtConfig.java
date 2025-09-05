package ru.perevalov.gamerecommenderai.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Order(1)
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public Algorithm hmac256Algorithm() {
        return Algorithm.HMAC256(jwtSecret);
    }

    @Bean
    public JWTVerifier jwtVerifier() {
        return JWT.require(hmac256Algorithm()).build();
    }

}
