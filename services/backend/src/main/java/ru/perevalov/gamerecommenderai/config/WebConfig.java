package ru.perevalov.gamerecommenderai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.perevalov.gamerecommenderai.controller.resolver.OpenIdResponseMethodArgumentResolver;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${application.security.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Регистрируем новый MethodArgumentResolver в ApplicationContext
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new OpenIdResponseMethodArgumentResolver());
    }

    /**
     * Настройка CORS для фронтенда
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
