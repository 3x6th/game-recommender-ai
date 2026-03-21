package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.message.MessageMetaType;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageMixedPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReplyPayloadDto;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.pipeline.PipelineSupport;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;

/**
 * Шаг сохранения ответа ассистента.
 */
@Component
@RequiredArgsConstructor
public class PersistAssistantStep implements PipelineStep, Ordered {
    private final ChatMessageService chatMessageService;
    private final PipelineSupport support;

    /**
     * Сохраняет сообщение ассистента и meta, если ответ успешен.
     *
     * @param context контекст обработки
     * @return обновленный контекст
     */
    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.getResponse() == null || context.getErrorMessage() != null || context.getAssistantMessageId() != null) {
            return Mono.just(context);
        }

        GameRecommendationResponse response = context.getResponse();
        String assistantText = response.getRecommendation() != null ? response.getRecommendation() : "";
        List<MessageCardDto> items = support.buildCards(response.getRecommendations());

        MessageMetaType type = items.isEmpty() ? MessageMetaType.REPLY : MessageMetaType.MIXED;
        Object payload = items.isEmpty()
                ? new MessageReplyPayloadDto(assistantText, null, null, context.getResponseSnapshot())
                : new MessageMixedPayloadDto(assistantText, items, context.getResponseSnapshot());

        return chatMessageService.appendAssistantMessage(context.getChatId(), assistantText, type, payload)
                .map(messageId -> {
                    context.setAssistantMessageId(messageId);
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
        return PipelineStepOrder.PERSIST_ASSISTANT;
    }
}
