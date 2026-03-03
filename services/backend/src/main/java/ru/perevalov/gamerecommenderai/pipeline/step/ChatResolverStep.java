package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.ChatsService;

/**
 * Шаг определения чата и проверки идемпотентности.
 */
@Component
@RequiredArgsConstructor
public class ChatResolverStep implements PipelineStep, Ordered {
    private final ChatsService chatsService;
    private final ChatMessageService chatMessageService;

    /**
     * Определяет chatId и признак дубликата по clientRequestId.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        UUID clientRequestId = context.getClientRequestId();
        UUID requestedChatId = context.getRequestedChatId();

        if (clientRequestId != null) {
            return chatMessageService.findLatestUserMessage(context.getUserContext(), clientRequestId)
                    .map(message -> applyDuplicate(context, message))
                    .switchIfEmpty(chatsService.getOrCreateChatId(requestedChatId, context.getUserContext())
                            .map(chatId -> applyChat(context, chatId)));
        }

        return chatsService.getOrCreateChatId(requestedChatId, context.getUserContext())
                .map(chatId -> applyChat(context, chatId));
    }

    /**
     * Возвращает порядок выполнения шага в pipeline.
     *
     * @return порядок выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.CHAT_RESOLVER;
    }

    /**
     * Помечает запрос как дубликат и переносит найденный chatId в контекст.
     *
     * @param context контекст обработки
     * @param message найденное пользовательское сообщение
     * @return обновленный контекст
     */
    private PipelineContext applyDuplicate(PipelineContext context, ChatMessage message) {
        context.setChatId(message.getChatId());
        context.setDuplicate(true);
        return context;
    }

    /**
     * Сохраняет chatId для нового или найденного чата.
     *
     * @param context контекст обработки
     * @param chatId идентификатор чата
     * @return обновленный контекст
     */
    private PipelineContext applyChat(PipelineContext context, UUID chatId) {
        context.setChatId(chatId);
        context.setDuplicate(false);
        return context;
    }
}
