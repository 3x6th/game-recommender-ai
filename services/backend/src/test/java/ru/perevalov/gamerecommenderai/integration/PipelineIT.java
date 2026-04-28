package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.client.GameRecommenderGrpcClient;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;
import ru.perevalov.gamerecommenderai.grpc.GameRecommendation;
import ru.perevalov.gamerecommenderai.grpc.RecommendationResponse;
import ru.perevalov.gamerecommenderai.repository.ChatMessageRepository;
import ru.perevalov.gamerecommenderai.service.SteamService;

/**
 * Интеграционные тесты recommendation pipeline.
 */
@AutoConfigureWebTestClient
@Tag("integration")
class PipelineIT extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private GameRecommenderGrpcClient grpcClient;

    @MockBean
    private SteamService steamService;

    /**
     * Проверяет happy-path, в котором сохраняются и USER-, и ASSISTANT-сообщения.
     *
     * @throws Exception если не удалось распарсить JSON-ответ
     */
    /**
     * PCAI-141: содержимое /proceed messages[0] должно быть идентично записи
     * в БД и тому, что отдаст GET /chats/{chatId}/messages для того же
     * сообщения. Любая утечка legacy-полей или потеря карточек ловится здесь.
     */
    @Test
    void pipeline_happyPath_proceedAndHistory_returnSameMessage() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Recommend me an open-world game")
                .tags(new String[]{"Open World"})
                .steamId("76561198000000010")
                .build();

        JsonNode proceedResponse = executePipeline(request, null);

        UUID chatId = UUID.fromString(proceedResponse.path("chatId").asText());
        JsonNode proceedMessages = proceedResponse.path("messages");
        assertThat(proceedMessages.isArray()).isTrue();
        assertThat(proceedMessages.size()).isEqualTo(1);

        JsonNode proceedAssistant = proceedMessages.get(0);
        UUID assistantMessageId = UUID.fromString(proceedAssistant.path("messageId").asText());

        // Карточки не должны быть пустыми: PCAI-141 кейс №1.
        JsonNode items = proceedAssistant.path("meta").path("payload").path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThan(0);
        JsonNode firstCard = items.get(0);
        assertThat(firstCard.path("title").asText()).isEqualTo("Example Game");
        assertThat(firstCard.path("genre").asText()).isEqualTo("RPG");
        assertThat(firstCard.path("description").asText()).isEqualTo("Example description");
        assertThat(firstCard.path("whyRecommended").asText()).isEqualTo("Great story");
        assertThat(firstCard.path("rating").asDouble()).isEqualTo(8.5);
        assertThat(firstCard.path("releaseYear").asText()).isEqualTo("2020");
        // Стимовские поля не должны протекать в контракт (PCAI-148).
        assertThat(firstCard.has("gameId")).isFalse();
        assertThat(firstCard.has("storeUrl")).isFalse();
        assertThat(firstCard.has("imageUrl")).isFalse();

        // GET /api/v1/chats/{chatId}/messages должен отдать ровно ту же запись.
        byte[] historyBytes = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/chats/{chatId}/messages")
                        .queryParam("before", java.time.LocalDateTime.now().plusYears(1))
                        .queryParam("limit", 10)
                        .build(chatId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(historyBytes).isNotNull();
        JsonNode history = objectMapper.readTree(new String(historyBytes, StandardCharsets.UTF_8));
        assertThat(history.isArray()).isTrue();

        JsonNode historyAssistant = null;
        for (JsonNode m : history) {
            if (m.path("messageId").asText().equals(assistantMessageId.toString())) {
                historyAssistant = m;
                break;
            }
        }
        assertThat(historyAssistant).as("assistant message in history").isNotNull();
        // Главный инвариант: meta-envelope из /proceed и из истории идентичен.
        assertThat(historyAssistant.path("meta")).isEqualTo(proceedAssistant.path("meta"));
        assertThat(historyAssistant.path("role").asText()).isEqualTo(proceedAssistant.path("role").asText());
        assertThat(historyAssistant.path("content").asText()).isEqualTo(proceedAssistant.path("content").asText());
    }

    @Test
    void pipeline_happyPath_persistsUserAndAssistant() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Recommend me an RPG")
                .tags(new String[]{"RPG"})
                .steamId("76561198000000000")
                .build();

        byte[] responseBytes = webTestClient.post()
                .uri("/api/v1/games/proceed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(responseBytes).isNotNull();
        JsonNode response = objectMapper.readTree(new String(responseBytes, StandardCharsets.UTF_8));

        assertThat(response.path("chatId").asText()).isNotBlank();
        JsonNode messagesJson = response.path("messages");
        assertThat(messagesJson.isArray()).isTrue();
        assertThat(messagesJson.size()).isEqualTo(1);

        JsonNode assistantJson = messagesJson.get(0);
        assertThat(assistantJson.path("messageId").asText()).isNotBlank();
        assertThat(assistantJson.path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(assistantJson.path("content").asText()).isNotBlank();
        assertThat(assistantJson.path("meta").path("type").asText())
                .isIn("reply", "mixed", "cards");
        assertThat(assistantJson.path("createdAt").asText()).isNotBlank();

        UUID chatId = UUID.fromString(response.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(messages.get(1).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(1).getContent()).isEqualTo("Recommend me an RPG");
        assertThat(messages.get(0).getContent()).isNotBlank();
        assertThat(messages.get(0).getCreatedAt()).isNotNull();
        assertThat(messages.get(1).getCreatedAt()).isNotNull();
    }

    /**
     * Проверяет сценарий soft-failure AI, в котором сохраняется только USER-сообщение.
     *
     * @throws Exception если не удалось распарсить JSON-ответ
     */
    @Test
    void pipeline_aiError_returnsSoftFailureAndPersistsOnlyUser() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(RecommendationResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("AI unavailable")
                        .build()));

        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Need advice")
                .tags(new String[]{"Indie"})
                .steamId("76561198000000001")
                .build();

        byte[] responseBytes = webTestClient.post()
                .uri("/api/v1/games/proceed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(responseBytes).isNotNull();
        JsonNode response = objectMapper.readTree(new String(responseBytes, StandardCharsets.UTF_8));

        assertThat(response.path("chatId").asText()).isNotBlank();
        JsonNode messagesJson = response.path("messages");
        assertThat(messagesJson.isArray()).isTrue();
        assertThat(messagesJson.size()).isEqualTo(1);

        JsonNode errorJson = messagesJson.get(0);
        assertThat(errorJson.path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(errorJson.path("meta").path("type").asText()).isEqualTo("error");
        assertThat(errorJson.path("meta").path("payload").path("message").asText()).isNotBlank();
        assertThat(errorJson.path("meta").path("payload").path("retryable").asBoolean()).isTrue();

        UUID chatId = UUID.fromString(response.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("Need advice");
    }

    /**
     * Проверяет идемпотентность, когда {@code clientRequestId} передан в теле запроса.
     *
     * @throws Exception если не удалось распарсить JSON-ответ
     */
    @Test
    void pipeline_idempotency_clientRequestId_doesNotDuplicateUserMessages() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        String clientRequestId = UUID.randomUUID().toString();
        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Same request")
                .tags(new String[]{"Action"})
                .steamId("76561198000000002")
                .clientRequestId(clientRequestId)
                .build();

        JsonNode first = executePipeline(request, null);
        JsonNode second = executePipeline(request, null);

        String firstAssistantId = first.path("messages").get(0).path("messageId").asText();
        String secondAssistantId = second.path("messages").get(0).path("messageId").asText();
        assertThat(firstAssistantId).isEqualTo(secondAssistantId);

        UUID chatId = UUID.fromString(first.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        long userCount = messages.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = messages.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();

        assertThat(userCount).isEqualTo(1);
        assertThat(assistantCount).isEqualTo(1);

        ChatMessage userMessage = messages.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .findFirst()
                .orElseThrow();

        assertThat(userMessage.getClientRequestId()).isEqualTo(UUID.fromString(clientRequestId));
        assertThat(userMessage.getMeta().get("payload").get("clientRequestId").asText())
                .isEqualTo(clientRequestId);
    }

    /**
     * Выполняет POST-запрос в pipeline с необязательным заголовком {@code X-Client-Request-Id}.
     *
     * @param request тело запроса
     * @param clientRequestId необязательное значение заголовка
     * @return распарсенный JSON-ответ
     * @throws Exception если не удалось распарсить JSON-ответ
     */
    private JsonNode executePipeline(GameRecommendationRequest request, String clientRequestId) throws Exception {
        WebTestClient.RequestBodySpec spec = webTestClient.post()
                .uri("/api/v1/games/proceed")
                .contentType(MediaType.APPLICATION_JSON);

        if (clientRequestId != null) {
            spec = spec.header("X-Client-Request-Id", clientRequestId);
        }

        byte[] responseBytes = spec
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(responseBytes).isNotNull();
        return objectMapper.readTree(new String(responseBytes, StandardCharsets.UTF_8));
    }

    /**
     * Строит успешный mock gRPC-ответ для интеграционных тестов.
     *
     * @return успешный ответ recommendation service
     */
    private RecommendationResponse successResponse() {
        GameRecommendation rec = GameRecommendation.newBuilder()
                .setTitle("Example Game")
                .setGenre("RPG")
                .setDescription("Example description")
                .setWhyRecommended("Great story")
                .setRating(8.5)
                .setReleaseYear("2020")
                .build();

        return RecommendationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .addRecommendations(rec)
                .build();
    }

    @Test
    void pipeline_idempotency_clientRequestIdFromHeader_isFallback() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        String clientRequestId = UUID.randomUUID().toString();
        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Header fallback request")
                .tags(new String[]{"RPG"})
                .steamId("76561198000000003")
                .build();

        JsonNode first = executePipeline(request, clientRequestId);
        JsonNode second = executePipeline(request, clientRequestId);

        UUID chatId = UUID.fromString(first.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        long userCount = messages.stream().filter(m -> m.getRole() == MessageRole.USER).count();

        assertThat(userCount).isEqualTo(1);

        ChatMessage userMessage = messages.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .findFirst()
                .orElseThrow();

        assertThat(userMessage.getClientRequestId()).isEqualTo(UUID.fromString(clientRequestId));
        assertThat(userMessage.getMeta().get("payload").get("clientRequestId").asText())
                .isEqualTo(clientRequestId);
    }

    @Test
    void pipeline_whenClientRequestIdMissing_generatesItOnBackend() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Generated request id")
                .tags(new String[]{"Indie"})
                .steamId("76561198000000004")
                .build();

        JsonNode response = executePipeline(request, null);

        UUID chatId = UUID.fromString(response.get("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();

        ChatMessage userMessage = messages.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .findFirst()
                .orElseThrow();

        assertThat(userMessage.getClientRequestId()).isNotNull();
        assertThat(userMessage.getMeta().get("payload").get("clientRequestId").asText()).isNotBlank();
    }
}
