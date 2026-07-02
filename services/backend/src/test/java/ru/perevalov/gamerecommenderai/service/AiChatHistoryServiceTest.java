package ru.perevalov.gamerecommenderai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.AiChatHistoryMessage;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;

class AiChatHistoryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatMessageRepository repository = Mockito.mock(ChatMessageRepository.class);

    @Test
    void includesUserAndAssistantCardsInChronologicalOrder() {
        UUID chatId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        ChatMessage user = message(MessageRole.USER, "Show me space games", null);
        ChatMessage assistant = message(MessageRole.ASSISTANT, "", cardsMeta());
        when(repository.findAiHistoryBeforeMessage(chatId, currentMessageId, 10))
                .thenReturn(Flux.just(assistant, user));

        AiChatHistoryService service = new AiChatHistoryService(repository, 10, 8000);

        StepVerifier.create(service.load(chatId, currentMessageId))
                .assertNext(history -> {
                    assertThat(history).hasSize(2);
                    assertThat(history.get(0).role()).isEqualTo(AiChatHistoryMessage.Role.USER);
                    assertThat(history.get(0).text()).isEqualTo("Show me space games");
                    assertThat(history.get(1).role()).isEqualTo(AiChatHistoryMessage.Role.ASSISTANT);
                    assertThat(history.get(1).text())
                            .contains("Strong narrative choices")
                            .contains("Outer Wilds (Adventure): Exploration and mystery");
                })
                .verifyComplete();

        verify(repository).findAiHistoryBeforeMessage(chatId, currentMessageId, 10);
    }

    @Test
    void keepsNewestMessagesWithinCharacterBudget() {
        UUID chatId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        ChatMessage newest = message(MessageRole.ASSISTANT, "12345", null);
        ChatMessage older = message(MessageRole.USER, "67890", null);
        when(repository.findAiHistoryBeforeMessage(chatId, currentMessageId, 10))
                .thenReturn(Flux.just(newest, older));

        AiChatHistoryService service = new AiChatHistoryService(repository, 10, 5);

        StepVerifier.create(service.load(chatId, currentMessageId))
                .assertNext(history -> assertThat(history)
                        .containsExactly(new AiChatHistoryMessage(AiChatHistoryMessage.Role.ASSISTANT, "12345")))
                .verifyComplete();
    }

    private ChatMessage message(MessageRole role, String content, ObjectNode meta) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setMeta(meta);
        return message;
    }

    private ObjectNode cardsMeta() {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("type", "cards");
        ObjectNode payload = meta.putObject("payload");
        ArrayNode items = payload.putArray("items");
        items.addObject().put("kind", "reasoning").put("text", "Strong narrative choices");
        items.addObject()
                .put("kind", "game")
                .put("title", "Outer Wilds")
                .put("genre", "Adventure")
                .put("whyRecommended", "Exploration and mystery");
        return meta;
    }
}
