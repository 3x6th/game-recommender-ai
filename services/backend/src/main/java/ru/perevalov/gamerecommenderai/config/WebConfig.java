package ru.perevalov.gamerecommenderai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.perevalov.gamerecommenderai.controller.resolver.OpenIdResponseMethodArgumentResolver;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Регистрируем новый MethodArgumentResolver в ApplicationContext
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new OpenIdResponseMethodArgumentResolver());
    }
}
