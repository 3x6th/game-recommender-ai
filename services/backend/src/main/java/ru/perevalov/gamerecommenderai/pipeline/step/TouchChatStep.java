package ru.perevalov.gamerecommenderai.pipeline.step;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.service.ChatsService;

/**
 * Шаг обновления времени активности чата.
 */
@Component
@RequiredArgsConstructor
public class TouchChatStep implements PipelineStep, Ordered {
    private final ChatsService chatsService;

    /**
     * Обновляет timestamp активности чата.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.getChatId() == null || context.getErrorMessage() != null) {
            return Mono.just(context);
        }
        return chatsService.touch(context.getChatId())
                .thenReturn(context);
    }

    /**
     * Возвращает порядок выполнения шага в pipeline.
     *
     * @return порядок выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.TOUCH_CHAT;
    }
}
