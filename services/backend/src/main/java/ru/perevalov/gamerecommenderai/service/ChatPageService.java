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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPageService {

    @Value("${app.chats.pagination.default-limit}")
    private int paginationDefaultLimit;

    @Value("${app.chats.pagination.max-limit}")
    private int paginationMaxLimit;

    @Value("${app.chats.pagination.min-limit}")
    private int paginationMinLimit;

    private final ChatsRepository chatsRepository;
    private final ChatMapper chatMapper;

    private static final int OFFSET_DEFAULT_VALUE = 0;

    /**
     * Возвращает страницу чатов пользователя, отсортированных по {@code updated_at DESC}.
     * <p>
     * Значения {@code requestLimit} и {@code requestOffset} валидируются:
     * {@code null} или значение вне допустимого диапазона ({@code app.chats.pagination.*})
     * заменяются дефолтными. Отрицательный {@code requestOffset} заменяется {@code 0}.
     *
     * @param userId        идентификатор пользователя
     * @param requestLimit  максимальное количество элементов на странице
     * @param requestOffset смещение от начала выборки
     * @return {@link Mono} с {@link ChatPageResponse}, содержащим список чатов и {@code totalElements}
     * @implNote Запросы данных и счётчика выполняются независимо (две отдельные SQL-операции).
     * Это inherent limitation limit/offset пагинации: если между запросами
     * будет добавлен новый чат, {@code totalElements} окажется больше числа
     * возвращённых элементов.
     */
    public Mono<ChatPageResponse> getChatPageByUserId(UUID userId, Integer requestLimit, Integer requestOffset) {
        int validatedLimit = validateLimitAndAdjustIfNeeded(requestLimit);
        int validatedOffset = validateOffsetAndAdjustIfNeeded(requestOffset);
        return chatsRepository.findAllByUserIdOrderByUpdatedAtDesc(userId, validatedLimit, validatedOffset)
                .map(chatMapper::toDto)
                .collectList()
                .zipWith(chatsRepository.countByUserId(userId), (chatList, totalElements) ->
                        new ChatPageResponse(
                                chatList,
                                validatedLimit,
                                validatedOffset,
                                totalElements
                        )
                );
    }

    /**
     * Возвращает страницу чатов гостевой сессии, отсортированных по {@code updated_at DESC}.
     * <p>
     * Логика валидации параметров аналогична {@link #getChatPageByUserId}.
     *
     * @param sessionId     идентификатор гостевой сессии
     * @param requestLimit  максимальное количество элементов на странице
     * @param requestOffset смещение от начала выборки
     * @return {@link Mono} с {@link ChatPageResponse}, содержащим список чатов и {@code totalElements}
     */
    public Mono<ChatPageResponse> getChatPageBySessionId(String sessionId, Integer requestLimit, Integer requestOffset) {
        int validatedLimit = validateLimitAndAdjustIfNeeded(requestLimit);
        int validatedOffset = validateOffsetAndAdjustIfNeeded(requestOffset);
        return chatsRepository.findAllBySessionIdOrderByUpdatedAtDesc(sessionId, validatedLimit, validatedOffset)
                .map(chatMapper::toDto)
                .collectList()
                .zipWith(chatsRepository.countBySessionId(sessionId), (chatList, totalElements) ->
                        new ChatPageResponse(
                                chatList,
                                validatedLimit,
                                validatedOffset,
                                totalElements
                        )
                );
    }

    private int validateLimitAndAdjustIfNeeded(Integer requestLimit) {
        int limit;
        if (requestLimit == null) {
            limit = paginationDefaultLimit;
        } else if (requestLimit < paginationMinLimit || requestLimit > paginationMaxLimit) {
            log.warn(
                    "Invalid chat pagination limit value: {}. Using default limit: {}",
                    requestLimit,
                    paginationDefaultLimit
            );
            limit = paginationDefaultLimit;
        } else {
            limit = requestLimit;
        }
        return limit;
    }

    private int validateOffsetAndAdjustIfNeeded(Integer requestOffset) {
        int offset;
        if (requestOffset == null) {
            offset = OFFSET_DEFAULT_VALUE;
        } else if (requestOffset < 0) {
            log.warn(
                    "Invalid chat pagination offset value: {}. Using default offset: {}",
                    requestOffset,
                    OFFSET_DEFAULT_VALUE
            );
            offset = OFFSET_DEFAULT_VALUE;
        } else {
            offset = requestOffset;
        }
        return offset;
    }
}
