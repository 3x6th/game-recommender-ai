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
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

/**
 * Шаг вызова AI и переиспользования сохранённого ответа при идемпотентном дубле.
 *
 * <p>При дубле (ChatResolverStep пометил {@code duplicate=true}) находим
 * последнее ASSISTANT-сообщение в чате и кладём его entity в
 * {@code context.assistantMessages}. {@code PersistAssistantStep} увидит
 * выставленный {@code assistantMessageId} и пропустит запись, а
 * {@code ResponseStep} соберёт {@code ProceedResponse} из готовой записи.
 *
 * <p>Раньше тут жил {@code extractSnapshot()} — сериализованный
 * {@link GameRecommendationResponse} лежал внутри {@code meta.payload.extra}.
 * Этот костыль удалён: сохранённого сообщения вместе с {@code meta} достаточно,
 * legacy DTO нам больше не нужен в pipeline после persist.
 */
@Component
@RequiredArgsConstructor
public class AiCallStep implements PipelineStep, Ordered {
    private final GameRecommenderService gameRecommenderService;
    private final ChatMessageService chatMessageService;

    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.isDuplicate()) {
            return findReusableAssistant(context)
                    .switchIfEmpty(callAi(context));
        }
        return callAi(context);
    }

    @Override
    public int getOrder() {
        return PipelineStepOrder.AI_CALL;
    }

    private Mono<PipelineContext> callAi(PipelineContext context) {
        return gameRecommenderService.getGameRecommendationsWithContext(
                        context.getRequest(),
                        context.getChatId().toString())
                .map(response -> {
                    context.setResponse(response);
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
     * При дубле кладёт сохранённое ASSISTANT-сообщение в контекст и помечает,
     * что persist-шаг пропускать (через {@code assistantMessageId}).
     */
    private Mono<PipelineContext> findReusableAssistant(PipelineContext context) {
        return chatMessageService.findLastAssistantMessage(context.getChatId())
                .map(message -> {
                    context.setAssistantMessageId(message.getId());
                    context.getAssistantMessages().add(message);
                    return context;
                });
    }
}
