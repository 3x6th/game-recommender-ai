package ru.perevalov.gamerecommenderai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import ru.perevalov.gamerecommenderai.filter.JwtRequestFilter;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Configuration
//@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    //private final JwtRequestFilter jwtRequestFilter;

    //TODO: Старая цепочка фильтров закомментированна, т.к. работает на сервлетах (Tomcat), что не позволяет использовать
    // реактивные контроллеры. В pom.xml пока была оставлена зависимость "spring-boot-starter-web", т.к. без нее
    // приложение падает в попытке сконфигурировать бины спринг секьюрити, которые зависят от неё. А чтобы все поднималось
    // все-таки на реактивном netty, в проперти добавлена "spring.main.web-application-type=reactive"
    // ----------
    // Итак, в итоге на данный момент имеем:
    // + В помнике есть зависимости и для tomcat, и для реактивного netty
    // + Внизу бин конфигурации минимальной реактивной секьюрити filter cahin, пропускающий все
    // + Старый filter chain был закомментирован во избежание конфликта при старте
    // + Весь функционал, связанный со старым секьюрити и все нереактивные контроллеры >>>> НЕ РАБОТАЮТ <<<<
    // (если не будет возвращено старое состояние)
    // ----------
    // Чтобы вернуть старое состояние, при котором работают нереактивные эндпоинты и вся старая секьюрити система,
    // необходимо разкомментировать в этом классе все, что сейчас закомменчено, а бин "springSecurityFilterChain" наоборот
    // закомментировать или удалить. Так-же в пропертях убрать, закомментить или переключить на "servlet" строку
    // "spring.main.web-application-type=reactive"
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
    }

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers(
//                                "/api/v1/auth/**",
//                                "/swagger-ui/**",
//                                "/swagger-ui.html",
//                                "/v3/api-docs/**",
//                                "/api-docs/**",
//                                "/actuator/health",
//                                "/actuator/prometheus"
//                        ).permitAll()
//                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
//                        .requestMatchers("/api/v1/private/**").hasAuthority(UserRole.USER.getAuthority())
//                        .anyRequest().authenticated()
//                )
//                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
}
