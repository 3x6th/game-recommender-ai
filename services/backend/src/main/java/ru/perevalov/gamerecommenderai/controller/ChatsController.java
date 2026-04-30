package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.chat.ChatMessageDto;
import ru.perevalov.gamerecommenderai.dto.chat.ChatPageResponse;
import ru.perevalov.gamerecommenderai.mapper.ChatMapper;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.ChatsService;
import ru.perevalov.gamerecommenderai.service.RequestContextResolver;

import java.util.UUID;

import static ru.perevalov.gamerecommenderai.util.TimeHelper.parseClientCursorInstant;

/**
 * Контроллер для работы с историей чатов пользователя.
 * <p>
 * Подход «толерантный clamp»: значения {@code limit}/{@code offset} вне диапазона не приводят
 * к {@code 400}, а молча ужимаются к границам, настроенным в {@code app.chats.pagination.*}.
 * Если фактически использованное значение отличается от запрошенного клиентом, в ответ
 * добавляются заголовки {@link #HEADER_LIMIT_ADJUSTED} / {@link #HEADER_OFFSET_ADJUSTED} —
 * клиент может их прочитать и сообщить об этом пользователю.
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatsController {

    static final String HEADER_LIMIT_ADJUSTED = "X-Pagination-Limit-Adjusted";
    static final String HEADER_OFFSET_ADJUSTED = "X-Pagination-Offset-Adjusted";

    private final ChatsService chatsService;
    private final ChatMessageService chatMessageService;
    private final RequestContextResolver requestContextResolver;
    private final ChatMapper chatMapper;

    /**
     * Возвращает постраничный список чатов текущего пользователя или гостевой сессии,
     * отсортированных по {@code updatedAt} в порядке убывания.
     *
     * @param limit  максимальное количество чатов в ответе; необязательный, ужимается к
     *               {@code app.chats.pagination.[min,max]-limit}
     * @param offset смещение от начала выборки для пагинации; необязательный, отрицательное → {@code 0}
     * @return страница со списком чатов и метаданными пагинации
     */
    @GetMapping
    public Mono<ResponseEntity<ChatPageResponse>> getUserChats(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return requestContextResolver.resolve()
                .flatMap(ctx -> chatsService.getUserChats(ctx, limit, offset))
                .map(response -> withAdjustmentHeaders(response, limit, offset));
    }

    /**
     * Возвращает историю сообщений чата постранично.
     * Доступ разрешён только владельцу чата (по {@code userId} или {@code sessionId}).
     *
     * @param chatId идентификатор чата
     * @param before верхняя граница выборки — возвращаются сообщения, отправленные до этого момента
     * @param limit  максимальное количество сообщений в ответе; необязательный, ужимается в сервисе
     * @return поток DTO сообщений в порядке убывания времени отправки
     */
    @GetMapping("/{chatId}/messages")
    public Flux<ChatMessageDto> getChatMessages(
            @PathVariable UUID chatId,
            @RequestParam String before,
            @RequestParam(required = false) Integer limit
    ) {
        return requestContextResolver.resolve()
                .flatMap(ctx -> chatsService.requireOwnership(chatId, ctx))
                .flatMapMany(chat -> chatMessageService.listBefore(chatId, parseClientCursorInstant(before), limit))
                .map(chatMapper::toDto);
    }

    private static ResponseEntity<ChatPageResponse> withAdjustmentHeaders(
            ChatPageResponse response, Integer requestedLimit, Integer requestedOffset
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (requestedLimit != null && requestedLimit != response.getLimit()) {
            builder.header(HEADER_LIMIT_ADJUSTED, String.valueOf(response.getLimit()));
        }
        if (requestedOffset != null && requestedOffset != response.getOffset()) {
            builder.header(HEADER_OFFSET_ADJUSTED, String.valueOf(response.getOffset()));
        }
        return builder.body(response);
    }
}
