package ru.perevalov.gamerecommenderai.pipeline;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.pipeline.step.AiCallStep;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallStepTest {

    @Mock
    private GameRecommenderService gameRecommenderService;

    @Mock
    private ChatMessageService chatMessageService;

    @Test
    void duplicateWithoutMatchingAssistantRetriesAiCall() {
        UUID chatId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("recommend something")
                .clientRequestId(clientRequestId.toString())
                .build();
        GameRecommendationResponse aiResponse = GameRecommendationResponse.builder()
                .success(true)
                .recommendations(List.of())
                .recommendation("try again")
                .build();
        PipelineContext context = duplicateContext(
                request,
                chatId,
                clientRequestId,
                userMessageId
        );

        when(chatMessageService.findAssistantMessage(chatId, clientRequestId))
                .thenReturn(Mono.empty());
        when(gameRecommenderService.getGameRecommendationsWithContext(
                request,
                chatId,
                userMessageId))
                .thenReturn(Mono.just(aiResponse));

        AiCallStep step = new AiCallStep(gameRecommenderService, chatMessageService);

        StepVerifier.create(step.handle(context))
                .assertNext(result -> assertThat(result.getResponse()).isSameAs(aiResponse))
                .verifyComplete();
    }

    @Test
    void duplicateWithMatchingAssistantReusesOnlyThatResponse() {
        UUID chatId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("recommend something")
                .clientRequestId(clientRequestId.toString())
                .build();
        PipelineContext context = duplicateContext(
                request,
                chatId,
                clientRequestId,
                userMessageId
        );
        ChatMessage assistant = new ChatMessage();
        assistant.setId(UUID.randomUUID());

        when(chatMessageService.findAssistantMessage(chatId, clientRequestId))
                .thenReturn(Mono.just(assistant));

        AiCallStep step = new AiCallStep(gameRecommenderService, chatMessageService);

        StepVerifier.create(step.handle(context))
                .assertNext(result -> {
                    assertThat(result.getAssistantMessageId()).isEqualTo(assistant.getId());
                    assertThat(result.getAssistantMessages()).containsExactly(assistant);
                })
                .verifyComplete();

        verify(gameRecommenderService, never()).getGameRecommendationsWithContext(
                request,
                chatId,
                userMessageId
        );
    }

    private static PipelineContext duplicateContext(
            GameRecommendationRequest request,
            UUID chatId,
            UUID clientRequestId,
            UUID userMessageId
    ) {
        PipelineContext context = new PipelineContext(request, null);
        context.setChatId(chatId);
        context.setClientRequestId(clientRequestId);
        context.setUserMessageId(userMessageId);
        context.setDuplicate(true);
        return context;
    }
}
