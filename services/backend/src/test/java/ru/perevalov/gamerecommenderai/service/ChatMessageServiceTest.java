package ru.perevalov.gamerecommenderai.service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.message.ChatMessageValidator;
import ru.perevalov.gamerecommenderai.message.MessageMetaFactory;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMetaFactory metaFactory = new MessageMetaFactory(objectMapper);

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageValidator chatMessageValidator;

    @Test
    void append_whenValidatorFails_thenNoSave() {
        ObjectNode meta = objectMapper.createObjectNode();
        UUID chatId = UUID.randomUUID();
        doThrow(new GameRecommenderException(ErrorType.INVALID_CHAT_MESSAGE, "bad"))
                .when(chatMessageValidator)
                .validateForAppend(chatId, MessageRole.USER, "hi", meta);

        ChatMessageService service = new ChatMessageService(
                chatMessageRepository,
                metaFactory,
                chatMessageValidator
        );

        StepVerifier.create(service.append(chatId, MessageRole.USER, "hi", meta))
                .expectError(GameRecommenderException.class)
                .verify();

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void append_whenValid_thenSavesAndSetsClientRequestId() {
        ObjectNode meta = objectMapper.createObjectNode();
        UUID chatId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        ChatMessage saved = new ChatMessage();
        saved.setId(UUID.randomUUID());

        when(chatMessageValidator.extractClientRequestId(meta)).thenReturn(clientRequestId);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(Mono.just(saved));

        ChatMessageService service = new ChatMessageService(
                chatMessageRepository,
                metaFactory,
                chatMessageValidator
        );

        StepVerifier.create(service.append(chatId, MessageRole.USER, "hi", meta))
                .assertNext(id -> assertThat(id).isEqualTo(saved.getId()))
                .verifyComplete();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getClientRequestId()).isEqualTo(clientRequestId);
    }

    @Test
    void appendUserMessage_whenValid_thenUsesReplyMeta() {
        ChatMessage saved = new ChatMessage();
        saved.setId(UUID.randomUUID());

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(Mono.just(saved));

        ChatMessageService service = new ChatMessageService(
                chatMessageRepository,
                metaFactory,
                chatMessageValidator
        );

        StepVerifier.create(service.appendUserMessage(UUID.randomUUID(), "hello", UUID.randomUUID(), null, null))
                .assertNext(id -> assertThat(id).isEqualTo(saved.getId()))
                .verifyComplete();

        ArgumentCaptor<JsonNode> metaCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(chatMessageValidator).validateForAppend(
                any(UUID.class),
                eq(MessageRole.USER),
                eq("hello"),
                metaCaptor.capture()
        );
        assertThat(metaCaptor.getValue().get(MessageMetaFields.FIELD_TYPE).asText()).isEqualTo("reply");
    }
}
