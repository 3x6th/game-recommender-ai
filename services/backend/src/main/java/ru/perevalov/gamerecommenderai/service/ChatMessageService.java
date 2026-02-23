package ru.perevalov.gamerecommenderai.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
 * Сервис сохранения сообщений чата и получения истории.
 * Валидация входных данных делегирована {@link ChatMessageValidator}.
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
     * @param chatId идентификатор чата
     * @param role роль сообщения
     * @param content текст сообщения
     * @param meta опциональные метаданные
     * @return идентификатор созданного сообщения
     */
    public Mono<UUID> append(UUID chatId, MessageRole role, String content, JsonNode meta) {
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
                            saved.getId(), chatId, role))
                    .map(ChatMessage::getId);
        });
    }

    /**
     * Добавляет сообщение роли USER и формирует meta-конверт ответа.
     *
     * @param chatId идентификатор чата
     * @param text текст сообщения
     * @param clientRequestId идентификатор клиентского запроса для идемпотентности/трекинга
     * @param tags опциональные теги
     * @param extra опциональная пользовательская нагрузка метаданных
     * @return идентификатор созданного сообщения
     */
    public Mono<UUID> appendUserMessage(
            UUID chatId,
            String text,
            UUID clientRequestId,
            List<String> tags,
            JsonNode extra
    ) {
        return Mono.defer(() -> {
            log.debug("Append USER message chatId={} clientRequestId={}", chatId, clientRequestId);
            ObjectNode meta = messageMetaFactory.reply(text, clientRequestId, tags, extra);
            return append(chatId, MessageRole.USER, text, meta);
        });
    }

    /**
     * Добавляет сообщение роли ASSISTANT с meta-конвертом.
     *
     * @param chatId идентификатор чата
     * @param content текст сообщения
     * @param type тип метаданных
     * @param payload нагрузка метаданных
     * @return идентификатор созданного сообщения
     */
    public Mono<UUID> appendAssistantMessage(UUID chatId, String content, MessageMetaType type, Object payload) {
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
     * Возвращает последние сообщения в порядке {@code created_at DESC, id DESC}.
     *
     * @param chatId идентификатор чата
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
     * Возвращает сообщения до указанного времени в порядке {@code created_at DESC, id DESC}.
     *
     * @param chatId идентификатор чата
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
     * Нормализует запрошенный размер страницы в пределах допустимых границ сервиса.
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
