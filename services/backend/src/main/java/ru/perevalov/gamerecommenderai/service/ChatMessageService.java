package ru.perevalov.gamerecommenderai.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.message.ChatMessageValidator;
import ru.perevalov.gamerecommenderai.message.MessageMetaFactory;
import ru.perevalov.gamerecommenderai.message.MessageMetaType;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;

/**
 * Сервис для сохранения сообщений чата и чтения истории.
 * Валидация сообщений и проверка meta-envelope делегированы {@link ChatMessageValidator}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    @Value("${app.chat.history.default-limit:50}")
    private int defaultLimit;

    @Value("${app.chat.history.max-limit:100}")
    private int maxLimit;

    private final ChatMessageRepository chatMessageRepository;
    private final MessageMetaFactory messageMetaFactory;
    private final ChatMessageValidator chatMessageValidator;

    /**
     * Добавляет сообщение в чат после валидации.
     *
     * @param chatId идентификатор целевого чата
     * @param role роль сообщения
     * @param content текст сообщения
     * @param meta необязательный metadata envelope
     * @return сохраненное сообщение
     */
    public Mono<ChatMessage> append(UUID chatId, MessageRole role, String content, JsonNode meta) {
        return Mono.defer(() -> {
            chatMessageValidator.validateForAppend(chatId, role, content, meta);

            ChatMessage message = new ChatMessage();
            message.setChatId(chatId);
            message.setRole(role);
            message.setContent(content);
            message.setMeta(meta);
            message.setClientRequestId(chatMessageValidator.extractClientRequestId(meta));

            return chatMessageRepository.save(message)
                    .doOnSuccess(saved -> log.info("Saved message id={} chatId={} role={}",
                            saved.getId(), chatId, role));
        });
    }

    /**
     * Добавляет USER-сообщение и формирует reply metadata envelope.
     * Если база отклоняет вставку из-за дубликата уникального ключа
     * {@code (chat_id, client_request_id)} для USER-сообщений, метод возвращает
     * уже существующее сообщение вместо ошибки, что делает retry race-safe.
     *
     * @param chatId идентификатор целевого чата
     * @param text текст сообщения
     * @param clientRequestId клиентский идентификатор запроса для идемпотентности и трассировки
     * @param tags необязательные теги
     * @param extra необязательная пользовательская metadata payload
     * @return сохраненное или уже существующее сообщение
     */
    public Mono<ChatMessage> appendUserMessage(
            UUID chatId,
            String text,
            UUID clientRequestId,
            List<String> tags,
            JsonNode extra
    ) {
        return Mono.defer(() -> {
            log.debug("Append USER message chatId={} clientRequestId={}", chatId, clientRequestId);
            ObjectNode meta = messageMetaFactory.reply(text, clientRequestId, tags, extra);

            Mono<ChatMessage> appendMono = append(chatId, MessageRole.USER, text, meta);

            if (clientRequestId == null) {
                return appendMono;
            }

            return appendMono.onErrorResume(DataIntegrityViolationException.class, ex ->
                    findLatestUserMessage(chatId, clientRequestId)
                            .switchIfEmpty(Mono.error(ex))
            );
        });
    }

    /**
     * Добавляет ASSISTANT-сообщение с metadata envelope.
     *
     * @param chatId идентификатор целевого чата
     * @param content текст сообщения
     * @param type тип метаданных
     * @param payload payload метаданных
     * @return сохраненное сообщение
     */
    public Mono<ChatMessage> appendAssistantMessage(UUID chatId, String content, MessageMetaType type, Object payload) {
        return Mono.defer(() -> {
            if (type == null) {
                return Mono.error(new GameRecommenderException(
                        ErrorType.INVALID_CHAT_MESSAGE, "assistant meta.type is invalid"));
            }

            log.debug("Append ASSISTANT message chatId={} type={}", chatId, type);
            ObjectNode meta = (type == MessageMetaType.REPLY && payload == null)
                    ? messageMetaFactory.reply(content)
                    : messageMetaFactory.envelope(type, payload);
            return append(chatId, MessageRole.ASSISTANT, content, meta);
        });
    }

    /**
     * Возвращает последнее USER-сообщение для конкретного чата и clientRequestId.
     * Если один из идентификаторов отсутствует, возвращает пустой результат.
     *
     * @param chatId идентификатор целевого чата
     * @param clientRequestId клиентский идентификатор запроса
     * @return найденное USER-сообщение или пустой результат
     */
    public Mono<ChatMessage> findLatestUserMessage(UUID chatId, UUID clientRequestId) {
        return Mono.defer(() -> {
            if (chatId == null || clientRequestId == null) {
                return Mono.empty();
            }
            return chatMessageRepository.findLatestUserByChatAndClientRequestId(chatId, clientRequestId);
        });
    }

    /**
     * Возвращает последнее USER-сообщение в рамках текущего owner scope,
     * определяемого userId или guest sessionId.
     *
     * @param ctx owner-контекст запроса
     * @param clientRequestId клиентский идентификатор запроса
     * @return найденное USER-сообщение или пустой результат
     */
    public Mono<ChatMessage> findLatestUserMessage(RequestContext ctx, UUID clientRequestId) {
        return Mono.defer(() -> {
            if (ctx == null || clientRequestId == null) {
                return Mono.empty();
            }
            return chatMessageRepository.findLatestUserByClientRequestId(
                    clientRequestId,
                    ctx.userId(),
                    ctx.sessionId()
            );
        });
    }

    /**
     * Возвращает последнее ASSISTANT-сообщение в чате.
     *
     * @param chatId идентификатор целевого чата
     * @return последнее ASSISTANT-сообщение или пустой результат
     */
    public Mono<ChatMessage> findLastAssistantMessage(UUID chatId) {
        return Mono.defer(() -> chatId == null
                ? Mono.empty()
                : chatMessageRepository.findLastAssistantByChatId(chatId));
    }

    /**
     * Возвращает последние сообщения чата, упорядоченные по {@code created_at DESC, id DESC}.
     *
     * @param chatId идентификатор целевого чата
     * @param limit запрошенный размер страницы
     * @return поток сообщений
     */
    public Flux<ChatMessage> listLast(UUID chatId, Integer limit) {
        return Mono.defer(() -> {
                    if (chatId == null) {
                        return Mono.error(new GameRecommenderException(
                                ErrorType.INVALID_CHAT_MESSAGE, "chatId is required"));
                    }
                    return Mono.just(normalizeLimit(limit));
                })
                .doOnNext(size -> log.debug("List last messages chatId={} limit={}", chatId, size))
                .flatMapMany(size -> chatMessageRepository.findLastByChatId(chatId, size));
    }

    /**
     * Возвращает сообщения чата, созданные до указанного момента времени,
     * упорядоченные по {@code created_at DESC, id DESC}.
     *
     * @param chatId идентификатор целевого чата
     * @param before эксклюзивная верхняя граница по времени
     * @param limit запрошенный размер страницы
     * @return поток сообщений
     */
    public Flux<ChatMessage> listBefore(UUID chatId, Instant before, Integer limit) {
        return Mono.defer(() -> {
                    if (chatId == null || before == null) {
                        return Mono.error(new GameRecommenderException(
                                ErrorType.INVALID_CHAT_MESSAGE, "chatId and before are required"));
                    }
                    return Mono.just(normalizeLimit(limit));
                })
                .doOnNext(size -> log.debug("List messages before chatId={} before={} limit={}",
                        chatId, before, size))
                .flatMapMany(size -> chatMessageRepository.findBeforeByChatId(chatId, before, size));
    }

    /**
     * Нормализует запрошенный размер страницы в пределах допустимых лимитов сервиса.
     *
     * @param limit запрошенный лимит
     * @return нормализованное значение лимита
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }
}
