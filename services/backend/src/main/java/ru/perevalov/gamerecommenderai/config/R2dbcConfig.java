package ru.perevalov.gamerecommenderai.config;

import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Option;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import ru.perevalov.gamerecommenderai.entity.converter.UserRoleEnumTypeConverter;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.List;

/**
 * Кофигурационный класс для корректного маппинга enum UserRole в соответствующий тип role_enum
 * в структуре базы данных game_recommender. Необходим для работы с колонкой "role" таблицы users.
 */
@Configuration
@RequiredArgsConstructor
public class R2dbcConfig extends AbstractR2dbcConfiguration {
    public static final String ROLE_ENUM_TYPE_NAME = "role_enum";
    public static final String EXTENSIONS_OPTION_NAME = "extensions";

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    private final UserRoleEnumTypeConverter userRoleEnumTypeConverter;

    @Override
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(r2dbcUrl);
    }

    @Override
    protected List<Object> getCustomConverters() {
        return List.of(userRoleEnumTypeConverter);
    }

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer connectionFactoryOptionsBuilderCustomizer() {
        return builder -> {
            builder.option(Option.valueOf(EXTENSIONS_OPTION_NAME),
                    List.of(EnumCodec.builder()
                            .withEnum(ROLE_ENUM_TYPE_NAME, UserRole.class)
                            .build()));
        };
    }
}