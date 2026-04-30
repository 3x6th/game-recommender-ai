package ru.perevalov.gamerecommenderai.pipeline.step;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.chat.ChatMessageDto;
import ru.perevalov.gamerecommenderai.dto.chat.ProceedResponse;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.mapper.ChatMapper;
import ru.perevalov.gamerecommenderai.message.MessageMetaFactory;
import ru.perevalov.gamerecommenderai.pipeline.PipelineContext;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStep;
import ru.perevalov.gamerecommenderai.pipeline.PipelineStepOrder;

/**
 * Финальный шаг сборки ответа pipeline в формате {@link ProceedResponse}.
 *
 * <p>Логика:
 * <ul>
 *     <li>если pipeline отработал штатно — отдаём ассистентские сообщения,
 *         сохраненные на предыдущих шагах ({@code context.assistantMessages});</li>
 *     <li>если на любом шаге выставлен {@code context.errorMessage}
 *         (soft-failure, см. {@code AiCallStep}) — собираем синтетическое
 *         {@code meta.type=error} сообщение прямо здесь, на лету,
 *         без записи в БД (это видимая юзеру ошибка, она не должна
 *         оседать в истории чата).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ResponseStep implements PipelineStep, Ordered {

    private static final String ERROR_CODE_AI_UNAVAILABLE = "AI_UNAVAILABLE";

    private final ChatMapper chatMapper;
    private final MessageMetaFactory metaFactory;

    @Override
    public Mono<PipelineContext> handle(PipelineContext context) {
        ProceedResponse response = ProceedResponse.builder()
                .chatId(context.getChatId())
                .messages(buildMessages(context))
                .build();
        context.setProceedResponse(response);
        return Mono.just(context);
    }

    @Override
    public int getOrder() {
        return PipelineStepOrder.RESPONSE;
    }

    private List<ChatMessageDto> buildMessages(PipelineContext context) {
        if (context.getErrorMessage() != null) {
            return List.of(buildErrorMessage(context));
        }
        return context.getAssistantMessages().stream()
                .map(chatMapper::toDto)
                .toList();
    }

    /**
     * Синтезирует ChatMessageDto с {@code meta.type=error} для отображения
     * пользовательской ошибки. Не сохраняется в БД — это деталь представления,
     * история чата ошибок не помнит.
     */
    private ChatMessageDto buildErrorMessage(PipelineContext context) {
        ObjectNode meta = metaFactory.error(ERROR_CODE_AI_UNAVAILABLE, context.getErrorMessage(), true);
        return new ChatMessageDto(
                UUID.randomUUID(),
                MessageRole.ASSISTANT,
                context.getErrorMessage(),
                (JsonNode) meta,
                Instant.now()
        );
    }
}
