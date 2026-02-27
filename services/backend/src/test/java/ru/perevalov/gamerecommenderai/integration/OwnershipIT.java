package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.ChatsService;
import ru.perevalov.gamerecommenderai.service.RequestContext;

@Tag("integration")
class OwnershipIT extends IntegrationTestBase {


    @Autowired
    private ChatsService chatsService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void requireOwnership_forForeignChat_returnsNotFound() {
        Mono<Void> flow = createUserAndGetId()
                .zipWith(createUserAndGetId())
                .flatMap(tuple -> {
                    UUID userA = tuple.getT1();
                    UUID userB = tuple.getT2();
                    RequestContext ctxA = RequestContext.forUser(userA, 123L, null);
                    RequestContext ctxB = RequestContext.forUser(userB, 456L, null);

                    return chatsService.getOrCreateChatId(null, ctxA)
                            .flatMap(chatId -> chatsService.requireOwnership(chatId, ctxB))
                            .then();
                });

        StepVerifier.create(flow)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.CHAT_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void writeToForeignChat_isBlockedAndDoesNotPersist() {
        AtomicReference<UUID> chatIdRef = new AtomicReference<>();
        Mono<Void> chatFlow = createUserAndGetId()
                .zipWith(createUserAndGetId())
                .flatMap(tuple -> {
                    UUID userA = tuple.getT1();
                    UUID userB = tuple.getT2();
                    RequestContext ctxA = RequestContext.forUser(userA, 123L, null);
                    RequestContext ctxB = RequestContext.forUser(userB, 456L, null);

                    return chatsService.getOrCreateChatId(null, ctxA)
                            .doOnNext(chatIdRef::set)
                            .flatMap(chatId -> chatsService.requireOwnership(chatId, ctxB)
                                    .flatMap(chat -> chatMessageService.appendUserMessage(
                                            chatId,
                                            "Should not be written",
                                            UUID.randomUUID(),
                                            null,
                                            null
                                    ))
                                    .then()
                            )
                            .then();
                });

        StepVerifier.create(chatFlow)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.CHAT_NOT_FOUND);
                })
                .verify();

        UUID chatId = chatIdRef.get();
        assertThat(chatId).isNotNull();

        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isEmpty();
    }

    private Mono<UUID> createUserAndGetId() {
        UUID userId = UUID.randomUUID();
        long steamId = Math.abs(UUID.randomUUID().getMostSignificantBits());

        return databaseClient.sql("""
                        INSERT INTO game_recommender.users
                            (id, steam_id, is_active, created_at, updated_at, role)
                        VALUES
                            (:id, :steamId, true, NOW(), NOW(), 'USER'::game_recommender.role_enum)
                        """)
                .bind("id", userId)
                .bind("steamId", steamId)
                .fetch()
                .rowsUpdated()
                .thenReturn(userId);
    }
}
