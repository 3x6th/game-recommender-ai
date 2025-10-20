package ru.perevalov.gamerecommenderai.config;

import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.Option;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.List;

/**
 * Кофигурационный Bean для корректного маппинга enum UserRole в соответствующий тип role_enum
 * в структуре базы данных game_recommender. Необходим для работы с колонкой "role" таблицы users.
 */
@Configuration
public class R2dbcConfig {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer connectionFactoryOptionsBuilderCustomizer() {
        return builder -> {
            builder.option(Option.valueOf("extensions"),
                    List.of(EnumCodec.builder()
                            .withEnum("role_enum", UserRole.class)
                            .build()));
        };
    }
}