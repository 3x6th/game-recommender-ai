package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;

@Tag("integration")
@Disabled("Depends on PCAI-115: cursor pagination with createdAt+id stability")
class PaginationIT extends IntegrationTestBase {

    @Autowired
    private ChatsRepository chatsRepository;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void listLastAndListBefore_areStableWithCreatedAtAndIdCursor() {
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        OffsetDateTime olderTime = baseTime.minusMinutes(1);

        Mono<UUID> flow = createChat()
                .flatMap(chatId -> seedMessages(chatId, baseTime, olderTime)
                        .thenReturn(chatId));

        StepVerifier.create(flow)
                .assertNext(chatId -> {
                    List<ChatMessage> page1 = chatMessageService.listLast(chatId, 10)
                            .collectList()
                            .block();

                    assertThat(page1).isNotNull();
                    assertThat(page1).hasSize(10);
                    assertSorted(page1);

                    Instant cursorCreatedAt = page1.get(page1.size() - 1)
                            .getCreatedAt()
                            .atOffset(ZoneOffset.UTC)
                            .toInstant();

                    List<ChatMessage> page2 = chatMessageService.listBefore(chatId, cursorCreatedAt, 10)
                            .collectList()
                            .block();

                    assertThat(page2).isNotNull();
                    assertThat(page2).hasSize(10);
                    assertSorted(page2);

                    Set<UUID> ids = new HashSet<>();
                    page1.forEach(msg -> ids.add(msg.getId()));
                    page2.forEach(msg -> ids.add(msg.getId()));

                    assertThat(ids).hasSize(20);
                })
                .verifyComplete();
    }

    private Mono<UUID> createChat() {
        Chats chat = new Chats();
        chat.setSessionId("session-" + UUID.randomUUID());
        chat.setStatus(ChatStatus.ACTIVE);
        return chatsRepository.save(chat).map(Chats::getId);
    }

    private Mono<Void> seedMessages(UUID chatId, OffsetDateTime baseTime, OffsetDateTime olderTime) {
        List<MessageSeed> seeds = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            seeds.add(new MessageSeed(UUID.randomUUID(), chatId, baseTime, "msg-" + i));
        }
        for (int i = 0; i < 5; i++) {
            seeds.add(new MessageSeed(UUID.randomUUID(), chatId, olderTime, "old-" + i));
        }

        return Flux.fromIterable(seeds)
                .concatMap(seed -> insertMessage(seed.id(), seed.chatId(), seed.createdAt(), seed.content()))
                .then();
    }

    private Mono<Void> insertMessage(UUID id, UUID chatId, OffsetDateTime createdAt, String content) {
        String meta = "{\"schemaVersion\":1,\"type\":\"reply\",\"payload\":{\"text\":\"" + content + "\"}}";
        return databaseClient.sql("""
                        INSERT INTO game_recommender.chat_messages
                            (id, chat_id, role, content, meta, client_request_id, created_at, updated_at)
                        VALUES
                            (:id, :chatId, 'USER', :content, :meta::jsonb, NULL, :createdAt, :createdAt)
                        """)
                .bind("id", id)
                .bind("chatId", chatId)
                .bind("content", content)
                .bind("meta", meta)
                .bind("createdAt", createdAt)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private void assertSorted(List<ChatMessage> messages) {
        Comparator<ChatMessage> comparator = Comparator
                .comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatMessage::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        for (int i = 0; i < messages.size() - 1; i++) {
            ChatMessage current = messages.get(i);
            ChatMessage next = messages.get(i + 1);
            assertThat(comparator.compare(current, next)).isLessThanOrEqualTo(0);
        }
    }

    private record MessageSeed(UUID id, UUID chatId, OffsetDateTime createdAt, String content) {
    }
}
