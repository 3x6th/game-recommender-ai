package ru.perevalov.gamerecommenderai.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;

@Repository
public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessage, UUID> {

    Flux<ChatMessage> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    Flux<ChatMessage> findByChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID chatId,
            Instant before,
            Pageable pageable
    );

    /**
     * Возвращает последние сообщения в стабильном порядке.
     */
    @Query("""
            SELECT *
            FROM game_recommender.chat_messages
            WHERE chat_id = :chatId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """)
    Flux<ChatMessage> findLastByChatId(UUID chatId, int limit);

    /**
     * Возвращает сообщения до заданной даты (курсорная пагинация).
     */
    @Query("""
            SELECT *
            FROM game_recommender.chat_messages
            WHERE chat_id = :chatId
              AND created_at < :before
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """)
    Flux<ChatMessage> findBeforeByChatId(UUID chatId, Instant before, int limit);

    /**
     * Возвращает последнее сообщение пользователя по clientRequestId в рамках конкретного чата.
     */
    @Query("""
            SELECT *
            FROM game_recommender.chat_messages
            WHERE chat_id = :chatId
              AND client_request_id = :clientRequestId
              AND role = 'USER'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    Mono<ChatMessage> findLatestUserByChatAndClientRequestId(UUID chatId, UUID clientRequestId);

    /**
     * Возвращает последнее сообщение пользователя по clientRequestId в рамках владельца (user/session).
     */
    @Query("""
            SELECT cm.*
            FROM game_recommender.chat_messages cm
            JOIN game_recommender.chats c ON c.id = cm.chat_id
            WHERE cm.client_request_id = :clientRequestId
              AND cm.role = 'USER'
              AND (
                      (:userId IS NOT NULL AND c.user_id = :userId)
                   OR (:sessionId IS NOT NULL AND c.session_id = :sessionId)
              )
            ORDER BY cm.created_at DESC, cm.id DESC
            LIMIT 1
            """)
    Mono<ChatMessage> findLatestUserByClientRequestId(UUID clientRequestId, UUID userId, String sessionId);

    /**
     * Возвращает последнее сообщение ассистента в чате.
     */
    @Query("""
            SELECT *
            FROM game_recommender.chat_messages
            WHERE chat_id = :chatId
              AND role = 'ASSISTANT'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    Mono<ChatMessage> findLastAssistantByChatId(UUID chatId);
}
