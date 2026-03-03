package ru.perevalov.gamerecommenderai.pipeline.step;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;

/**
 * Финальный шаг сборки ответа pipeline.
 */
@Component
@RequiredArgsConstructor
public class ResponseStep implements PipelineStep, Ordered {

    /**
     * Заполняет служебные поля ответа: chatId, assistantMessageId и errorMessage.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        GameRecommendationResponse response = context.getResponse();
        if (response == null) {
            return Mono.just(context);
        }

        if (context.getChatId() != null) {
            response.setChatId(context.getChatId().toString());
        }
        if (context.getAssistantMessageId() != null) {
            response.setAssistantMessageId(context.getAssistantMessageId().toString());
        }
        if (context.getErrorMessage() != null) {
            response.setErrorMessage(context.getErrorMessage());
        }
        context.setResponse(response);
        return Mono.just(context);
    }

    /**
     * Возвращает порядок выполнения шага в pipeline.
     *
     * @return порядок выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.RESPONSE;
    }
}
