package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.Collections;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.pipeline.PipelineSupport;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

/**
 * Шаг вызова AI и повторного использования ответа при идемпотентности.
 */
@Component
@RequiredArgsConstructor
public class AiCallStep implements PipelineStep, Ordered {
    private final GameRecommenderService gameRecommenderService;
    private final ChatMessageService chatMessageService;
    private final PipelineSupport support;

    /**
     * Вызывает AI или переиспользует предыдущий ответ при повторном запросе.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.isDuplicate()) {
            return findReusableAssistant(context)
                    .switchIfEmpty(callAi(context));
        }
        return callAi(context);
    }

    /**
     * Возвращает порядок выполнения шага в pipeline.
     *
     * @return порядок выполнения шага
     */
    @Override
    public int getOrder() {
        return PipelineStepOrder.AI_CALL;
    }

    /**
     * Запрашивает рекомендации у AI и сохраняет снапшот ответа.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    private Mono<PipelineContext> callAi(PipelineContext context) {
        return gameRecommenderService.getGameRecommendationsWithContext(
                        context.getRequest(),
                        context.getChatId().toString())
                .map(response -> {
                    context.setResponse(response);
                    context.setResponseSnapshot(support.buildResponseSnapshot(response));
                    return context;
                })
                .onErrorResume(GameRecommenderException.class, ex -> {
                    if (ex.getErrorType() == ErrorType.GRPC_AI_ERROR) {
                        GameRecommendationResponse response = GameRecommendationResponse.builder()
                                .success(false)
                                .errorMessage(ex.getMessage())
                                .recommendations(Collections.emptyList())
                                .build();
                        context.setResponse(response);
                        context.setErrorMessage(ex.getMessage());
                        return Mono.just(context);
                    }
                    return Mono.error(ex);
                });
    }

    /**
     * Пытается переиспользовать ранее сохраненный ответ ассистента.
     *
     * @param context контекст обработки
     * @return обновленный контекст либо пустой поток, если переиспользование невозможно
     */
    private Mono<PipelineContext> findReusableAssistant(PipelineContext context) {
        return chatMessageService.findLastAssistantMessage(context.getChatId())
                .flatMap(message -> {
                    GameRecommendationResponse snapshot = support.extractSnapshot(message.getMeta());
                    if (snapshot == null) {
                        return Mono.empty();
                    }
                    context.setResponse(snapshot);
                    context.setAssistantMessageId(message.getId());
                    return Mono.just(context);
                });
    }
}
