package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.chat.ChatPageResponse;
import ru.perevalov.gamerecommenderai.mapper.ChatMapper;
import ru.perevalov.gamerecommenderai.repository.ChatsRepository;

import java.util.UUID;

/**
 * Сервис постраничной выборки чатов.
 * <p>
 * Стратегия «толерантного clamp»: невалидные {@code limit}/{@code offset} не приводят к 400, а
 * молча ужимаются к ближайшей границе из конфигурации. Факт подмены контроллер видит по разнице
 * между {@code requested} и {@link ChatPageResponse#getLimit()} / {@link ChatPageResponse#getOffset()}
 * и сообщает клиенту через ответные заголовки {@code X-Pagination-Limit-Adjusted} /
 * {@code X-Pagination-Offset-Adjusted}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPageService {

    @Value("${app.chats.pagination.default-limit:10}")
    private int paginationDefaultLimit;

    @Value("${app.chats.pagination.min-limit:1}")
    private int paginationMinLimit;

    @Value("${app.chats.pagination.max-limit:100}")
    private int paginationMaxLimit;

    private final ChatsRepository chatsRepository;
    private final ChatMapper chatMapper;

    private static final int OFFSET_MIN_VALUE = 0;

    /**
     * Возвращает страницу чатов пользователя, отсортированных по {@code updated_at DESC}.
     *
     * @param userId        идентификатор пользователя
     * @param requestLimit  максимальное количество элементов на странице; {@code null} → дефолт,
     *                      выходящее за {@code [min..max]} — clamp к границе
     * @param requestOffset смещение от начала выборки; {@code null} или отрицательное → {@code 0}
     * @return {@link Mono} с {@link ChatPageResponse}, содержащим список чатов и {@code totalElements}
     * @implNote Запросы данных и счётчика выполняются независимо (две отдельные SQL-операции).
     * Это inherent limitation limit/offset пагинации: если между запросами
     * будет добавлен новый чат, {@code totalElements} окажется больше числа
     * возвращённых элементов.
     */
    public Mono<ChatPageResponse> getChatPageByUserId(UUID userId, Integer requestLimit, Integer requestOffset) {
        int limit = effectiveLimit(requestLimit);
        int offset = effectiveOffset(requestOffset);
        return chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, limit, offset)
                .map(chatMapper::toDto)
                .collectList()
                .zipWith(chatsRepository.countByUserId(userId), (chatList, totalElements) ->
                        new ChatPageResponse(chatList, limit, offset, totalElements));
    }

    /**
     * Возвращает страницу чатов гостевой сессии, отсортированных по {@code updated_at DESC}.
     *
     * @param sessionId     идентификатор гостевой сессии
     * @param requestLimit  максимальное количество элементов на странице; {@code null} → дефолт,
     *                      выходящее за {@code [min..max]} — clamp к границе
     * @param requestOffset смещение от начала выборки; {@code null} или отрицательное → {@code 0}
     * @return {@link Mono} с {@link ChatPageResponse}, содержащим список чатов и {@code totalElements}
     */
    public Mono<ChatPageResponse> getChatPageBySessionId(String sessionId, Integer requestLimit, Integer requestOffset) {
        int limit = effectiveLimit(requestLimit);
        int offset = effectiveOffset(requestOffset);
        return chatsRepository.findAllBySessionIdOrderByUpdatedAtDesc(sessionId, limit, offset)
                .map(chatMapper::toDto)
                .collectList()
                .zipWith(chatsRepository.countBySessionId(sessionId), (chatList, totalElements) ->
                        new ChatPageResponse(chatList, limit, offset, totalElements));
    }

    private int effectiveLimit(Integer requestLimit) {
        if (requestLimit == null) {
            return paginationDefaultLimit;
        }
        if (requestLimit < paginationMinLimit) {
            log.debug("limit={} below min={}, clamping to min", requestLimit, paginationMinLimit);
            return paginationMinLimit;
        }
        if (requestLimit > paginationMaxLimit) {
            log.debug("limit={} above max={}, clamping to max", requestLimit, paginationMaxLimit);
            return paginationMaxLimit;
        }
        return requestLimit;
    }

    private int effectiveOffset(Integer requestOffset) {
        if (requestOffset == null || requestOffset < OFFSET_MIN_VALUE) {
            return OFFSET_MIN_VALUE;
        }
        return requestOffset;
    }
}
