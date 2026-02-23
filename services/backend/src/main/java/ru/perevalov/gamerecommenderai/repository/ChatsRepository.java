package ru.perevalov.gamerecommenderai.repository;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

@Repository
public interface ChatsRepository extends ReactiveCrudRepository<Chats, UUID> {

    Mono<Chats> findById(UUID id);

    Mono<Chats> findByIdAndUserId(UUID chatId, UUID userId);

    Mono<Chats> findByIdAndSessionId(UUID chatId, String sessionId);

    Flux<Chats> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, ChatStatus status, Pageable pageable);

    Flux<Chats> findBySessionIdAndStatusOrderByUpdatedAtDesc(String sessionId, ChatStatus status, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE game_recommender.chats
            SET updated_at = NOW()
            WHERE id = :chatId
            """)
    Mono<Integer> touch(UUID chatId);

    @Modifying
    @Query("""
            UPDATE game_recommender.chats
            SET user_id = :userId,
                session_id = NULL,
                updated_at = NOW()
            WHERE session_id = :sessionId
              AND user_id IS NULL
            """)
    Mono<Integer> bindGuestChatsToUser(String sessionId, UUID userId);
}
