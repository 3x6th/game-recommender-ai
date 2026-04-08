package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.chat.ChatMessageDto;
import ru.perevalov.gamerecommenderai.dto.chat.ChatPageResponse;
import ru.perevalov.gamerecommenderai.mapper.ChatMapper;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.ChatsService;
import ru.perevalov.gamerecommenderai.service.RequestContextResolver;

import java.time.LocalDateTime;
import java.util.UUID;

import static ru.perevalov.gamerecommenderai.util.TimeHelper.toSystemTimezoneInstant;

/**
 * Контроллер для работы с историей чатов пользователя.
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatsController {

    private final ChatsService chatsService;
    private final ChatMessageService chatMessageService;
    private final RequestContextResolver requestContextResolver;
    private final ChatMapper chatMapper;

    /**
     * Возвращает постраничный список чатов текущего пользователя,
     * отсортированных по UpdatedAt в порядке убывания.
     *
     * @param limit  максимальное количество чатов в ответе (необязательный)
     * @param offset смещение от начала выборки для пагинации (необязательный)
     * @return страница со списком чатов и метаданными пагинации
     */
    @GetMapping
    public Mono<ChatPageResponse> getUserChats(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return requestContextResolver.resolve()
                .flatMap(ctx -> chatsService.getUserChats(ctx, limit, offset));
    }

    /**
     * Возвращает историю сообщений чата постранично (курсорная пагинация по времени).
     * Доступ разрешён только владельцу чата.
     *
     * @param chatId идентификатор чата
     * @param before верхняя граница выборки — возвращаются сообщения, отправленные до этого момента
     * @param limit  максимальное количество сообщений в ответе (необязательный)
     * @return поток DTO сообщений в порядке убывания времени отправки
     */
    @GetMapping("/{chatId}/messages")
    public Flux<ChatMessageDto> getChatMessages(
            @PathVariable UUID chatId,
            @RequestParam LocalDateTime before,
            @RequestParam(required = false) Integer limit
    ) {
        return requestContextResolver.resolve()
                .flatMap(ctx -> chatsService.requireOwnership(chatId, ctx))
                .flatMapMany(chat -> chatMessageService.listBefore(chatId, toSystemTimezoneInstant(before), limit))
                .map(chatMapper::toDto);
    }
}
