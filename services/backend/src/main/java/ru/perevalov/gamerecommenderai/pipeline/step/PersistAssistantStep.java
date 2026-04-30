package ru.perevalov.gamerecommenderai.pipeline.step;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.message.MessageMetaType;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardsPayloadDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReplyPayloadDto;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;
import ru.perevalov.gamerecommenderai.pipeline.PipelineSupport;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;

/**
 * Шаг сохранения ответа ассистента в формате meta-envelope.
 *
 * <p>Структура meta для ассистента:
 * <ul>
 *     <li>есть карточки или reasoning → {@code type=cards} с
 *         полиморфными {@code payload.items[]} (см. {@link PipelineSupport#buildItems});</li>
 *     <li>пусто → {@code type=reply} с пустым текстом.</li>
 * </ul>
 *
 * <p>{@code content} сообщения умышленно пустой для cards — весь визуал
 * лежит в {@code items[]}. Дублирование reasoning в content было удалено
 * в рамках чистки PCAI-141 (см. контракт §1).
 */
@Component
@RequiredArgsConstructor
public class PersistAssistantStep implements PipelineStep, Ordered {
    private final ChatMessageService chatMessageService;
    private final PipelineSupport support;

    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        if (context.getResponse() == null
                || context.getErrorMessage() != null
                || context.getAssistantMessageId() != null) {
            return Mono.just(context);
        }

        GameRecommendationResponse response = context.getResponse();
        List<MessageItemDto> items = support.buildItems(
                response.getReasoning(),
                response.getRecommendations()
        );

        MessageMetaType type;
        Object payload;
        String content;
        if (items.isEmpty()) {
            type = MessageMetaType.REPLY;
            payload = new MessageReplyPayloadDto("", null, null, null);
            content = "";
        } else {
            type = MessageMetaType.CARDS;
            payload = new MessageCardsPayloadDto(items);
            content = "";
        }

        return chatMessageService.appendAssistantMessage(context.getChatId(), content, type, payload)
                .map(message -> {
                    context.setAssistantMessageId(message.getId());
                    context.getAssistantMessages().add(message);
                    return context;
                });
    }

    @Override
    public int getOrder() {
        return PipelineStepOrder.PERSIST_ASSISTANT;
    }
}
