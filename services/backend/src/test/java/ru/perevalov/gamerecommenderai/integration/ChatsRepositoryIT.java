package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;

class ChatsRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void touch_updatesExistingChat() {
        Mono<Integer> flow = createGuestChat("session-touch")
                .flatMap(chat -> chatsRepository.touch(chat.getId()));

        StepVerifier.create(flow)
                .assertNext(updated -> assertThat(updated).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void bindGuestChatsToUser_movesOnlyGuestChatsOfSession() {
        String targetSession = "session-bind-" + UUID.randomUUID();
        String otherSession = "session-other-" + UUID.randomUUID();

        Mono<Void> flow = createUserViaSql()
                .flatMap(userId -> createGuestChat(targetSession)
                        .flatMap(targetChat -> createGuestChat(otherSession)
                                .flatMap(otherChat -> {
                                    Chats alreadyOwned = new Chats();
                                    alreadyOwned.setSessionId(targetSession);
                                    alreadyOwned.setUserId(userId);
                                    alreadyOwned.setStatus(ChatStatus.ACTIVE);

                                    return chatsRepository.save(alreadyOwned)
                                            .flatMap(ignored -> chatsRepository.bindGuestChatsToUser(targetSession, userId))
                                            .flatMap(updated -> {
                                                assertThat(updated).isEqualTo(1);
                                                return Mono.zip(
                                                        chatsRepository.findById(targetChat.getId()),
                                                        chatsRepository.findById(otherChat.getId())
                                                );
                                            })
                                            .doOnNext(tuple -> {
                                                Chats rebound = tuple.getT1();
                                                Chats untouched = tuple.getT2();

                                                assertThat(rebound.getUserId()).isEqualTo(userId);
                                                assertThat(rebound.getSessionId()).isNull();
                                                assertThat(untouched.getUserId()).isNull();
                                                assertThat(untouched.getSessionId()).isEqualTo(otherSession);
                                            })
                                            .then();
                                })));

        StepVerifier.create(flow).verifyComplete();
    }

    private Mono<Chats> createGuestChat(String sessionId) {
        Chats chat = new Chats();
        chat.setSessionId(sessionId);
        chat.setStatus(ChatStatus.ACTIVE);
        return chatsRepository.save(chat);
    }

    private Mono<UUID> createUserViaSql() {
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
