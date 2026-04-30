package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.security.RequestIdentity;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты на ветви разрешения {@link RequestContext} из {@link RequestIdentity}:
 * аутентифицированный пользователь, гость по {@code sessionId} и анонимный запрос
 * без идентификации.
 */
@ExtendWith(MockitoExtension.class)
class RequestContextResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RequestContextResolver resolver;

    private static final long STEAM_ID = 76561197960287930L;

    @BeforeEach
    void setUp() {
        // Минимальная преинициализация: каждый тест сам подкладывает RequestIdentity в реактивный контекст.
    }

    @Test
    void resolve_whenSteamIdAndUserExists_thenReturnsUserContext() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findBySteamId(STEAM_ID)).thenReturn(Mono.just(user));

        Mono<RequestContext> result = resolver.resolve()
                .contextWrite(Context.of(RequestIdentity.class, new RequestIdentity(null, UserRole.USER, STEAM_ID)));

        StepVerifier.create(result)
                .assertNext(ctx -> {
                    assertThat(ctx.isUser()).isTrue();
                    assertThat(ctx.userId()).isEqualTo(userId);
                    assertThat(ctx.steamId()).isEqualTo(STEAM_ID);
                    assertThat(ctx.sessionId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void resolve_whenSteamIdButUserMissing_thenFailsWithUnauthorized() {
        when(userRepository.findBySteamId(STEAM_ID)).thenReturn(Mono.empty());

        Mono<RequestContext> result = resolver.resolve()
                .contextWrite(Context.of(RequestIdentity.class, new RequestIdentity(null, UserRole.USER, STEAM_ID)));

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.AUTHENTICATED_USER_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void resolve_whenGuestSessionId_thenReturnsGuestContext() {
        String sessionId = "session-" + UUID.randomUUID();

        Mono<RequestContext> result = resolver.resolve()
                .contextWrite(Context.of(RequestIdentity.class, RequestIdentity.guest(sessionId)));

        StepVerifier.create(result)
                .assertNext(ctx -> {
                    assertThat(ctx.isGuest()).isTrue();
                    assertThat(ctx.sessionId()).isEqualTo(sessionId);
                    assertThat(ctx.userId()).isNull();
                    assertThat(ctx.steamId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void resolve_whenAnonymous_thenFailsWithAuthenticationRequired() {
        Mono<RequestContext> result = resolver.resolve()
                .contextWrite(Context.of(RequestIdentity.class, RequestIdentity.anonymous()));

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_REQUIRED);
                })
                .verify();
    }

    @Test
    void resolve_whenSessionIdIsBlank_thenFailsWithAuthenticationRequired() {
        Mono<RequestContext> result = resolver.resolve()
                .contextWrite(Context.of(RequestIdentity.class, new RequestIdentity("   ", UserRole.GUEST, null)));

        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_REQUIRED);
                })
                .verify();
    }
}
