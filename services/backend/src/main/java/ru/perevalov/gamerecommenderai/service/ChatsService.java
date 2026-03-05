package ru.perevalov.gamerecommenderai.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.Chats;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;

/**
 * Сервис жизненного цикла чатов и проверки владения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatsService {

    private final ChatsRepository chatsRepository;

    /**
     * Возвращает существующий chat id при успешной проверке владения
     * или создает новый чат для текущего контекста.
     *
     * @param chatIdFromRequest chat id из запроса, может быть {@code null}
     * @param ctx вычисленный контекст запроса
     * @return chat id существующего или нового чата
     */
    public Mono<UUID> getOrCreateChatId(UUID chatIdFromRequest, RequestContext ctx) {
        return Mono.defer(() -> {
            if (chatIdFromRequest == null) {
                return createForContext(ctx)
                        .doOnNext(chat -> log.info("Created new chat id={} for userId={} sessionId={}",
                                chat.getId(), ctx != null ? ctx.userId() : null, ctx != null ? ctx.sessionId() : null))
                        .map(Chats::getId);
            }

            return findOwnedChat(chatIdFromRequest, ctx)
                    .switchIfEmpty(createForContext(ctx)
                            .doOnNext(chat -> log.warn("Ownership failed for chatId={}, created new chat id={}",
                                    chatIdFromRequest, chat.getId())))
                    .map(Chats::getId);
        });
    }

    /**
     * Проверяет, что чат принадлежит текущему контексту запроса.
     *
     * @param chatId идентификатор чата для проверки
     * @param ctx вычисленный контекст запроса
     * @return сущность принадлежащего чата
     */
    public Mono<Chats> requireOwnership(UUID chatId, RequestContext ctx) {
        return Mono.defer(() -> findOwnedChat(chatId, ctx)
                .switchIfEmpty(Mono.error(new GameRecommenderException(ErrorType.CHAT_NOT_FOUND, chatId)))
        );
    }

    /**
     * Создает активный гостевой чат, привязанный к session id.
     *
     * @param sessionId идентификатор гостевой сессии
     * @param aiAgentId идентификатор агента
     * @return созданный чат
     */
    public Mono<Chats> createForGuest(String sessionId, UUID aiAgentId) {
        return Mono.defer(() -> {
            if (sessionId == null || sessionId.isBlank()) {
                return Mono.error(new GameRecommenderException(
                        ErrorType.INVALID_REQUEST_CONTEXT, "sessionId is required for guest chat"));
            }

            Chats chat = new Chats();
            chat.setSessionId(sessionId);
            chat.setAiAgentId(aiAgentId);
            chat.setStatus(ChatStatus.ACTIVE);
            return chatsRepository.save(chat);
        });
    }

    /**
     * Создает активный пользовательский чат, привязанный к user id.
     *
     * @param userId идентификатор пользователя
     * @param aiAgentId идентификатор агента
     * @return созданный чат
     */
    public Mono<Chats> createForUser(UUID userId, UUID aiAgentId) {
        return Mono.defer(() -> {
            if (userId == null) {
                return Mono.error(new GameRecommenderException(
                        ErrorType.INVALID_REQUEST_CONTEXT, "userId is required for user chat"));
            }

            Chats chat = new Chats();
            chat.setUserId(userId);
            chat.setAiAgentId(aiAgentId);
            chat.setStatus(ChatStatus.ACTIVE);
            return chatsRepository.save(chat);
        });
    }

    /**
     * Обновляет метку времени чата ({@code updated_at}) без загрузки сущности.
     *
     * @param chatId идентификатор чата
     * @return сигнал завершения
     */
    public Mono<Void> touch(UUID chatId) {
        return Mono.defer(() -> {
            if (chatId == null) {
                return Mono.error(new GameRecommenderException(
                        ErrorType.INVALID_REQUEST_CONTEXT, "chatId is required"));
            }
            return chatsRepository.touch(chatId)
                    .doOnNext(count -> log.debug("Touch chatId={} rowsUpdated={}", chatId, count))
                    .flatMap(count -> count > 0
                            ? Mono.empty()
                            : Mono.error(new GameRecommenderException(ErrorType.CHAT_NOT_FOUND, chatId)))
                    .then();
        });
    }

    /**
     * Перепривязывает все гостевые чаты с session id на user id.
     *
     * @param sessionId идентификатор гостевой сессии
     * @param userId идентификатор пользователя
     * @return количество обновленных строк
     */
    public Mono<Integer> bindGuestChatsToUser(String sessionId, UUID userId) {
        return Mono.defer(() -> {
            if (userId == null) {
                return Mono.error(new GameRecommenderException(
                        ErrorType.INVALID_REQUEST_CONTEXT, "userId is required"));
            }
            if (sessionId == null || sessionId.isBlank()) {
                log.warn("Skip bind guest chats: sessionId is empty for userId={}", userId);
                return Mono.just(0);
            }
            return chatsRepository.bindGuestChatsToUser(sessionId, userId)
                    .doOnNext(count -> log.info("Bound guest chats to userId={} sessionId={} rowsUpdated={}",
                            userId, sessionId, count));
        });
    }

    /**
     * Создает чат для пользовательского или гостевого контекста запроса.
     *
     * @param ctx контекст запроса
     * @return созданный чат
     */
    private Mono<Chats> createForContext(RequestContext ctx) {
        if (ctx == null) {
            return Mono.error(new GameRecommenderException(
                    ErrorType.INVALID_REQUEST_CONTEXT, "request context is null"));
        }

        if (ctx.isUser()) {
            return createForUser(ctx.userId(), ctx.aiAgentId());
        }
        if (ctx.isGuest()) {
            return createForGuest(ctx.sessionId(), ctx.aiAgentId());
        }

        return Mono.error(new GameRecommenderException(
                ErrorType.INVALID_REQUEST_CONTEXT, "request context has no userId or sessionId"));
    }

    /**
     * Ищет чат только если он принадлежит владельцу текущего контекста.
     *
     * @param chatId идентификатор чата
     * @param ctx контекст запроса
     * @return принадлежащий чат или пустой результат
     */
    private Mono<Chats> findOwnedChat(UUID chatId, RequestContext ctx) {
        if (chatId == null) {
            return Mono.error(new GameRecommenderException(
                    ErrorType.INVALID_REQUEST_CONTEXT, "chatId is required"));
        }
        if (ctx == null) {
            return Mono.error(new GameRecommenderException(
                    ErrorType.INVALID_REQUEST_CONTEXT, "request context is null"));
        }

        if (ctx.isUser()) {
            return chatsRepository.findByIdAndUserId(chatId, ctx.userId());
        }
        if (ctx.isGuest()) {
            return chatsRepository.findByIdAndSessionId(chatId, ctx.sessionId());
        }

        return Mono.error(new GameRecommenderException(
                ErrorType.INVALID_REQUEST_CONTEXT, "request context has no userId or sessionId"));
    }
}
