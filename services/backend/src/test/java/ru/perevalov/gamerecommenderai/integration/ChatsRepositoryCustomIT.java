package ru.perevalov.gamerecommenderai.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты на {@link ru.perevalov.gamerecommenderai.repository.ChatsRepositoryCustomImpl}:
 * убеждаются, что {@code LEFT JOIN LATERAL} корректно возвращает превью последнего сообщения,
 * упорядочивание идёт по {@code updated_at DESC}, чаты без сообщений возвращаются с {@code null preview},
 * а гостевая ветка не пересекается с пользовательской.
 */
@Tag("integration")
class ChatsRepositoryCustomIT extends IntegrationTestBase {

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void findAllByUserIdOrderByUpdatedAtDesc_returnsLastMessagePreviewViaLateralJoin() {
        UUID userId = createUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        UUID newerChatId = saveUserChat(userId, now);
        UUID olderChatId = saveUserChat(userId, now.minusMinutes(5));
        UUID emptyChatId = saveUserChat(userId, now.minusMinutes(10));

        insertMessage(newerChatId, "first newer", now.minusMinutes(2));
        insertMessage(newerChatId, "latest newer", now.minusMinutes(1));
        insertMessage(olderChatId, "older preview", now.minusMinutes(6));

        StepVerifier.create(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, 10, 0).collectList())
                .assertNext(rows -> {
                    assertThat(rows).extracting(ChatWithLastMessageProjection::getId)
                            .containsExactly(newerChatId, olderChatId, emptyChatId);

                    assertThat(rows).extracting(ChatWithLastMessageProjection::getLastMessagePreview)
                            .containsExactly("latest newer", "older preview", null);

                    assertThat(rows).extracting(ChatWithLastMessageProjection::getStatus)
                            .containsOnly(ChatStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void findAllByUserIdOrderByUpdatedAtDesc_appliesLimitAndOffset() {
        UUID userId = createUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        for (int i = 0; i < 5; i++) {
            saveUserChat(userId, now.minusMinutes(i));
        }

        StepVerifier.create(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, 2, 1).collectList())
                .assertNext(rows -> assertThat(rows).hasSize(2))
                .verifyComplete();
    }

    @Test
    void findAllBySessionIdOrderByUpdatedAtDesc_doesNotReturnUserChats() {
        String sessionId = "session-" + UUID.randomUUID();
        UUID otherUserId = createUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

        UUID guestChatId = saveGuestChat(sessionId, now);
        saveUserChat(otherUserId, now);

        insertMessage(guestChatId, "guest preview", now.minusMinutes(1));

        StepVerifier.create(chatsRepository.findAllBySessionIdOrderByUpdatedAtDesc(sessionId, 10, 0).collectList())
                .assertNext(rows -> {
                    assertThat(rows).extracting(ChatWithLastMessageProjection::getId)
                            .containsExactly(guestChatId);
                    assertThat(rows.get(0).getLastMessagePreview()).isEqualTo("guest preview");
                })
                .verifyComplete();
    }

    @Test
    void findAllByUserIdOrderByUpdatedAtDesc_returnsEmptyForUnknownUser() {
        UUID unknown = UUID.randomUUID();
        StepVerifier.create(chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(unknown, 10, 0).collectList())
                .assertNext(rows -> assertThat(rows).isEmpty())
                .verifyComplete();
    }

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        long steamId = Math.abs(UUID.randomUUID().getMostSignificantBits());

        databaseClient.sql("""
                        INSERT INTO game_recommender.users
                            (id, steam_id, is_active, created_at, updated_at, role)
                        VALUES
                            (:id, :steamId, true, NOW(), NOW(), 'USER'::game_recommender.role_enum)
                        """)
                .bind("id", userId)
                .bind("steamId", steamId)
                .fetch()
                .rowsUpdated()
                .block();

        return userId;
    }

    private UUID saveUserChat(UUID userId, OffsetDateTime updatedAt) {
        Chats chat = new Chats();
        chat.setUserId(userId);
        chat.setStatus(ChatStatus.ACTIVE);

        UUID chatId = chatsRepository.save(chat).map(Chats::getId).block();
        forceUpdatedAt(chatId, updatedAt);
        return chatId;
    }

    private UUID saveGuestChat(String sessionId, OffsetDateTime updatedAt) {
        Chats chat = new Chats();
        chat.setSessionId(sessionId);
        chat.setStatus(ChatStatus.ACTIVE);

        UUID chatId = chatsRepository.save(chat).map(Chats::getId).block();
        forceUpdatedAt(chatId, updatedAt);
        return chatId;
    }

    private void forceUpdatedAt(UUID chatId, OffsetDateTime updatedAt) {
        databaseClient.sql("""
                        UPDATE game_recommender.chats
                        SET updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .bind("id", chatId)
                .bind("updatedAt", updatedAt)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void insertMessage(UUID chatId, String content, OffsetDateTime createdAt) {
        Long inserted = databaseClient.sql("""
                        INSERT INTO game_recommender.chat_messages
                            (id, chat_id, role, content, meta, client_request_id, created_at, updated_at)
                        VALUES
                            (:id, :chatId, :role, :content, :meta::jsonb, NULL, :createdAt, :createdAt)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("chatId", chatId)
                .bind("role", MessageRole.USER.name())
                .bind("content", content)
                .bind("meta", "{\"schemaVersion\":1,\"type\":\"reply\",\"payload\":{\"text\":\"" + content + "\"}}")
                .bind("createdAt", createdAt)
                .fetch()
                .rowsUpdated()
                .block();

        assertThat(inserted).isEqualTo(1L);
    }
}
