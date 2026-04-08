package ru.perevalov.gamerecommenderai.repository;

import reactor.core.publisher.Flux;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;

import java.util.UUID;

public interface ChatsRepositoryCustom {

    Flux<ChatWithLastMessageProjection> findAllByUserIdOrderByUpdatedAtDesc(UUID userId, int limit, long offset);

    Flux<ChatWithLastMessageProjection> findAllBySessionIdOrderByUpdatedAtDesc(String sessionId, int limit, long offset);
}
