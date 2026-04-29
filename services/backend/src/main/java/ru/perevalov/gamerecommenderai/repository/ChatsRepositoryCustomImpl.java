package ru.perevalov.gamerecommenderai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatsRepositoryCustomImpl implements ChatsRepositoryCustom {

    private final DatabaseClient databaseClient;

    private static final String CHATS_WITH_LAST_MESSAGE_BY_USER = """
            SELECT c.id, c.status, c.updated_at, m.content AS last_message_preview
            FROM game_recommender.chats c
            LEFT JOIN LATERAL (
                SELECT content FROM game_recommender.chat_messages
                WHERE chat_id = c.id AND role = 'USER' AND content <> ''
                ORDER BY created_at DESC, id DESC LIMIT 1
            ) m ON true
            WHERE c.user_id = :userId
            ORDER BY c.updated_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String CHATS_WITH_LAST_MESSAGE_BY_SESSION = """
            SELECT c.id, c.status, c.updated_at, m.content AS last_message_preview
            FROM game_recommender.chats c
            LEFT JOIN LATERAL (
                SELECT content FROM game_recommender.chat_messages
                WHERE chat_id = c.id AND role = 'USER' AND content <> ''
                ORDER BY created_at DESC, id DESC LIMIT 1
            ) m ON true
            WHERE c.session_id = :sessionId
            ORDER BY c.updated_at DESC
            LIMIT :limit OFFSET :offset
            """;

    @Override
    public Flux<ChatWithLastMessageProjection> findAllByUserIdOrderByUpdatedAtDesc(UUID userId, int limit, long offset) {
        return databaseClient.sql(CHATS_WITH_LAST_MESSAGE_BY_USER)
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> mapRow(row))
                .all();
    }

    @Override
    public Flux<ChatWithLastMessageProjection> findAllBySessionIdOrderByUpdatedAtDesc(String sessionId, int limit, long offset) {
        return databaseClient.sql(CHATS_WITH_LAST_MESSAGE_BY_SESSION)
                .bind("sessionId", sessionId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> mapRow(row))
                .all();
    }

    private ChatWithLastMessageProjection mapRow(io.r2dbc.spi.Row row) {
        return new ChatWithLastMessageRow(
                row.get("id", UUID.class),
                ChatStatus.valueOf(row.get("status", String.class)),
                row.get("updated_at", LocalDateTime.class),
                row.get("last_message_preview", String.class)
        );
    }

    private record ChatWithLastMessageRow(
            UUID id,
            ChatStatus status,
            LocalDateTime updatedAt,
            String lastMessagePreview
    ) implements ChatWithLastMessageProjection {
        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public ChatStatus getStatus() {
            return status;
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public String getLastMessagePreview() {
            return lastMessagePreview;
        }
    }
}
