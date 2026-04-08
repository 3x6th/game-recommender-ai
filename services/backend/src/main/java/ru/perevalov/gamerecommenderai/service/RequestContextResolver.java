package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;

@Component
@RequiredArgsConstructor
public class RequestContextResolver {

    private final UserRepository userRepository;

    /**
     * Строит {@link RequestContext} из {@link RequestIdentity}, помещённого в реактивный контекст.
     * <p>
     * Логика разрешения:
     * <ol>
     *   <li>Если {@code identity.steamId != null} — ищет пользователя в БД по Steam ID
     *       и возвращает {@link RequestContext#forUser}. Если пользователь не найден —
     *       завершается ошибкой {@code AUTHENTICATED_USER_NOT_FOUND}.</li>
     *   <li>Если {@code identity.sessionId} непустой — возвращает {@link RequestContext#forGuest}.</li>
     *   <li>Иначе — завершается ошибкой {@code AUTHENTICATION_REQUIRED}.</li>
     * </ol>
     *
     * @return {@link Mono} с {@link RequestContext} для текущего запроса
     */
    public Mono<RequestContext> resolve() {
        return Mono.deferContextual(ctx -> {
            RequestIdentity identity = ctx.getOrDefault(RequestIdentity.class, RequestIdentity.anonymous());

            if (identity.steamId() != null) {
                return userRepository.findBySteamId(identity.steamId())
                        .map(user -> RequestContext.forUser(user.getId(), identity.steamId(), null))
                        .switchIfEmpty(Mono.error(new GameRecommenderException(ErrorType.AUTHENTICATED_USER_NOT_FOUND)));
            }

            if (identity.sessionId() != null && !identity.sessionId().isBlank()) {
                return Mono.just(RequestContext.forGuest(identity.sessionId(), null));
            }

            return Mono.error(new GameRecommenderException(ErrorType.AUTHENTICATION_REQUIRED));
        });
    }
}
