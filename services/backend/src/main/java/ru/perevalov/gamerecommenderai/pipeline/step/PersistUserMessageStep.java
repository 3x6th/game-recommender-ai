package ru.perevalov.gamerecommenderai.pipeline.step;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;

/**
 * Шаг сохранения сообщения пользователя.
 */
@Component
@RequiredArgsConstructor
public class PersistUserMessageStep implements PipelineStep, Ordered {
    private final ChatMessageService chatMessageService;

    /**
     * Сохраняет сообщение пользователя, если запрос не является дубликатом.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.isDuplicate()) {
            return Mono.just(context);
        }
        return chatMessageService.appendUserMessage(
                        context.getChatId(),
                        context.getRequest().getContent(),
                        context.getClientRequestId(),
                        context.getTags(),
                        null)
                .map(messageId -> {
                    context.setUserMessageId(messageId);
                    return context;
                });
    }

    /**
     * Возвращает порядок выполнения шага в pipeline.
     *
     * @return порядок выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.PERSIST_USER_MESSAGE;
    }
}
