package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

import java.util.UUID;

@Repository
public interface ChatsRepository extends ReactiveCrudRepository<Chats, UUID> {

    Mono<Chats> findById(UUID id);

    Mono<Chats> findByIdAndUserId(UUID chatId, UUID userId);

    Mono<Chats> findByIdAndSessionId(UUID chatId, String sessionId);

    Flux<Chats> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, ChatStatus status, Pageable pageable);

    Flux<Chats> findBySessionIdAndStatusOrderByUpdatedAtDesc(String sessionId, ChatStatus status, Pageable pageable);
}
