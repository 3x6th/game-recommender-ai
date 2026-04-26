package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.chat.ChatDto;
import ru.perevalov.gamerecommenderai.dto.chat.ChatPageResponse;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.mapper.ChatMapper;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты на бизнес-логику постраничной выборки чатов в {@link ChatPageService}.
 * Покрывают подстановку дефолтов при {@code null}, толерантный {@code clamp}
 * при выходе за границы и сборку {@link ChatPageResponse} через {@code zipWith} счётчика.
 */
@ExtendWith(MockitoExtension.class)
class ChatPageServiceTest {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    @Mock
    private ChatsRepository chatsRepository;

    @Mock
    private ChatMapper chatMapper;

    @InjectMocks
    private ChatPageService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "paginationDefaultLimit", DEFAULT_LIMIT);
        ReflectionTestUtils.setField(service, "paginationMinLimit", MIN_LIMIT);
        ReflectionTestUtils.setField(service, "paginationMaxLimit", MAX_LIMIT);
    }

    @Test
    void getChatPageByUserId_whenLimitAndOffsetNull_thenAppliesDefaults() {
        UUID userId = UUID.randomUUID();
        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.getChatPageByUserId(userId, null, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    assertThat(response.getLimit()).isEqualTo(DEFAULT_LIMIT);
                    assertThat(response.getOffset()).isZero();
                })
                .verifyComplete();

        verify(chatsRepository).findAllByUserIdOrderByUpdatedAtDesc(userId, DEFAULT_LIMIT, 0L);
    }

    @Test
    void getChatPageByUserId_whenLimitAboveMax_thenClampedToMax() {
        UUID userId = UUID.randomUUID();
        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.getChatPageByUserId(userId, MAX_LIMIT + 50, 5))
                .assertNext(response -> {
                    assertThat(response.getLimit()).isEqualTo(MAX_LIMIT);
                    assertThat(response.getOffset()).isEqualTo(5);
                })
                .verifyComplete();

        verify(chatsRepository).findAllByUserIdOrderByUpdatedAtDesc(userId, MAX_LIMIT, 5L);
    }

    @Test
    void getChatPageByUserId_whenLimitBelowMin_thenClampedToMin() {
        UUID userId = UUID.randomUUID();
        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.getChatPageByUserId(userId, 0, 0))
                .assertNext(response -> assertThat(response.getLimit()).isEqualTo(MIN_LIMIT))
                .verifyComplete();

        verify(chatsRepository).findAllByUserIdOrderByUpdatedAtDesc(userId, MIN_LIMIT, 0L);
    }

    @Test
    void getChatPageByUserId_whenOffsetNegative_thenClampedToZero() {
        UUID userId = UUID.randomUUID();
        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.getChatPageByUserId(userId, 5, -10))
                .assertNext(response -> assertThat(response.getOffset()).isZero())
                .verifyComplete();

        verify(chatsRepository).findAllByUserIdOrderByUpdatedAtDesc(userId, 5, 0L);
    }

    @Test
    void getChatPageByUserId_whenLimitWithinRange_thenForwardsAsIs() {
        UUID userId = UUID.randomUUID();
        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.getChatPageByUserId(userId, 25, 50))
                .assertNext(response -> {
                    assertThat(response.getLimit()).isEqualTo(25);
                    assertThat(response.getOffset()).isEqualTo(50);
                })
                .verifyComplete();

        verify(chatsRepository).findAllByUserIdOrderByUpdatedAtDesc(userId, 25, 50L);
    }

    @Test
    void getChatPageByUserId_whenRepositoryReturnsRows_thenMapsAndZipsTotalElements() {
        UUID userId = UUID.randomUUID();
        ChatWithLastMessageProjection projection = stubProjection();
        ChatDto dto = new ChatDto(projection.getId(), projection.getStatus(),
                projection.getUpdatedAt(), projection.getLastMessagePreview());

        when(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(eq(userId), anyInt(), anyLong()))
                .thenReturn(Flux.just(projection));
        when(chatMapper.toDto(projection)).thenReturn(dto);
        when(chatsRepository.countByUserId(userId)).thenReturn(Mono.just(42L));

        StepVerifier.create(service.getChatPageByUserId(userId, 5, 0))
                .assertNext(response -> {
                    assertThat(response.getContent()).containsExactly(dto);
                    assertThat(response.getTotalElements()).isEqualTo(42L);
                    assertThat(response.getLimit()).isEqualTo(5);
                    assertThat(response.getOffset()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void getChatPageBySessionId_whenLimitNull_thenUsesDefault() {
        String sessionId = "session-" + UUID.randomUUID();
        when(chatsRepository.findAllBySessionIdOrderByUpdatedAtDesc(eq(sessionId), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countBySessionId(sessionId)).thenReturn(Mono.just(7L));

        StepVerifier.create(service.getChatPageBySessionId(sessionId, null, 100))
                .assertNext(response -> {
                    assertThat(response.getLimit()).isEqualTo(DEFAULT_LIMIT);
                    assertThat(response.getOffset()).isEqualTo(100);
                    assertThat(response.getTotalElements()).isEqualTo(7L);
                })
                .verifyComplete();

        verify(chatsRepository).findAllBySessionIdOrderByUpdatedAtDesc(sessionId, DEFAULT_LIMIT, 100L);
    }

    @Test
    void getChatPageBySessionId_whenInvokedSeparatePaths_thenDoesNotTouchUserPath() {
        String sessionId = "session-" + UUID.randomUUID();
        when(chatsRepository.findAllBySessionIdOrderByUpdatedAtDesc(anyString(), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(chatsRepository.countBySessionId(anyString())).thenReturn(Mono.just(0L));

        service.getChatPageBySessionId(sessionId, 1, 0).block();

        verify(chatsRepository, times(0)).findAllByUserIdOrderByUpdatedAtDesc(any(), anyInt(), anyLong());
        verify(chatsRepository, times(0)).countByUserId(any());
    }

    private static ChatWithLastMessageProjection stubProjection() {
        UUID id = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.now();
        return new ChatWithLastMessageProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public ChatStatus getStatus() {
                return ChatStatus.ACTIVE;
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return updatedAt;
            }

            @Override
            public String getLastMessagePreview() {
                return "preview";
            }
        };
    }
}
