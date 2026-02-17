package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessage, UUID> {

    Flux<ChatMessage> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    Flux<ChatMessage> findByChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID chatId,
            Instant before,
            Pageable pageable
    );
}
