package ru.perevalov.gamerecommenderai.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
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
}
