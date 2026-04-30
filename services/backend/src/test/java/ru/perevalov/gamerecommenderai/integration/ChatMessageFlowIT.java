package ru.perevalov.gamerecommenderai.integration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.message.MessageMetaType;
import ru.perevalov.gamerecommenderai.message.dto.MessageErrorPayloadDto;
import ru.perevalov.gamerecommenderai.service.ChatMessageService;
import ru.perevalov.gamerecommenderai.service.ChatsService;
import ru.perevalov.gamerecommenderai.service.RequestContext;

class ChatMessageFlowIT extends IntegrationTestBase {

    @Autowired
    private ChatsService chatsService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createChat_appendUserAndAssistant_listLast() {
        ObjectNode extra = objectMapper.createObjectNode().put("debug", true);
        UUID clientRequestId = UUID.randomUUID();
        Mono<List<ru.perevalov.gamerecommenderai.entity.ChatMessage>> flow = createUserAndGetId()
                .map(userId -> RequestContext.forUser(userId, 123L, null))
                .flatMap(ctx -> chatsService.getOrCreateChatId(null, ctx)
                        .flatMap(chatId ->
                                chatMessageService.appendUserMessage(chatId, "hi", clientRequestId, List.of("RPG"), extra)
                                        .then(chatMessageService.appendAssistantMessage(chatId, "hello", MessageMetaType.REPLY, null))
                                        .thenMany(chatMessageService.listLast(chatId, 10))
                                        .collectList()
                        ));

        StepVerifier.create(flow)
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
                    assertThat(list.get(0).getContent()).isEqualTo("hello");
                    assertThat(list.get(0).getMeta().get("type").asText()).isEqualTo("reply");
                    assertThat(list.get(1).getRole()).isEqualTo(MessageRole.USER);
                    assertThat(list.get(1).getContent()).isEqualTo("hi");
                    assertThat(list.get(1).getClientRequestId()).isEqualTo(clientRequestId);
                    assertThat(list.get(1).getMeta().get("payload").get("clientRequestId").asText())
                            .isEqualTo(clientRequestId.toString());
                })
                .verifyComplete();
    }

    @Test
    void aiError_isPersistedAsAssistantErrorMessage() {
        MessageErrorPayloadDto payload = new MessageErrorPayloadDto("AI_TIMEOUT", "Upstream timeout", true);
        Mono<List<ru.perevalov.gamerecommenderai.entity.ChatMessage>> flow = createUserAndGetId()
                .map(userId -> RequestContext.forUser(userId, 123L, null))
                .flatMap(ctx -> chatsService.getOrCreateChatId(null, ctx)
                        .flatMap(chatId ->
                                chatMessageService.appendUserMessage(chatId, "need help", UUID.randomUUID(), null, null)
                                        .then(chatMessageService.appendAssistantMessage(chatId, null, MessageMetaType.ERROR, payload))
                                        .thenMany(chatMessageService.listLast(chatId, 10))
                                        .collectList()
                        ));

        StepVerifier.create(flow)
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
                    assertThat(list.get(0).getContent()).isNull();
                    assertThat(list.get(0).getMeta().get("type").asText()).isEqualTo("error");
                    assertThat(list.get(0).getMeta().get("payload").get("code").asText()).isEqualTo("AI_TIMEOUT");
                    assertThat(list.get(0).getMeta().get("payload").get("message").asText()).isEqualTo("Upstream timeout");
                    assertThat(list.get(0).getMeta().get("payload").get("retryable").asBoolean()).isTrue();
                    assertThat(list.get(1).getRole()).isEqualTo(MessageRole.USER);
                    assertThat(list.get(1).getContent()).isEqualTo("need help");
                })
                .verifyComplete();
    }

    @Test
    void чужойChatId_forGetOrCreate_failsWithChatNotFound() {
        RequestContext guestCtx = RequestContext.forGuest("session-guest-1", null);
        Mono<Void> flow = createUserAndGetId()
                .map(userId -> RequestContext.forUser(userId, 123L, null))
                .flatMap(userCtx -> chatsService.getOrCreateChatId(null, userCtx)
                        .flatMap(originalChatId ->
                                chatsService.getOrCreateChatId(originalChatId, guestCtx)
                                        .then()
                        ));

        StepVerifier.create(flow)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.CHAT_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void requireOwnership_whenGuestAndUserChat_thenFails() {
        RequestContext guestCtx = RequestContext.forGuest("session-guest-2", null);
        Mono<Void> flow = createUserAndGetId()
                .map(userId -> RequestContext.forUser(userId, 123L, null))
                .flatMap(userCtx -> chatsService.getOrCreateChatId(null, userCtx)
                        .flatMap(chatId -> chatsService.requireOwnership(chatId, guestCtx))
                        .then());

        StepVerifier.create(flow)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(GameRecommenderException.class);
                    GameRecommenderException gre = (GameRecommenderException) ex;
                    assertThat(gre.getErrorType()).isEqualTo(ErrorType.CHAT_NOT_FOUND);
                })
                .verify();
    }

    private Mono<UUID> createUserAndGetId() {
        UUID userId = UUID.randomUUID();
        long steamId = Math.abs(UUID.randomUUID().getMostSignificantBits());

        return databaseClient.sql("""
                        INSERT INTO game_recommender.users
                            (id, steam_id, is_active, created_at, updated_at, role)
                        VALUES
                            (:id, :steamId, true, NOW(), NOW(), 'USER'::game_recommender.role_enum)
                        """)
                .bind("id", userId)
                .bind("steamId", steamId)
                .fetch()
                .rowsUpdated()
                .thenReturn(userId);
    }
}
