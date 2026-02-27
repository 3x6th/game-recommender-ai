package ru.perevalov.gamerecommenderai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
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

@AutoConfigureWebTestClient
@Tag("integration")
@Disabled("Depends on PCAI-113/115/116/117: chat storage, read API, idempotency, ownership")
class ProceedIT extends IntegrationTestBase {

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

    @Test
    void proceed_happyPath_persistsUserAndAssistant() throws Exception {
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

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("recommendation").asText()).isNotBlank();
        assertThat(response.path("chatId").asText()).isNotBlank();
        assertThat(response.path("assistantMessageId").asText()).isNotBlank();

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

    @Test
    void proceed_aiError_returnsSoftFailureAndPersistsOnlyUser() throws Exception {
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

        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("errorMessage").asText()).isNotBlank();
        assertThat(response.path("chatId").asText()).isNotBlank();

        UUID chatId = UUID.fromString(response.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("Need advice");
    }

    @Test
    void proceed_idempotency_clientRequestId_doesNotDuplicateUserMessages() throws Exception {
        when(steamService.getOwnedGames(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(Mono.just(new SteamOwnedGamesResponse()));
        when(grpcClient.getGameRecommendations(any()))
                .thenReturn(Mono.just(successResponse()));

        String clientRequestId = UUID.randomUUID().toString();
        GameRecommendationRequest request = GameRecommendationRequest.builder()
                .content("Same request")
                .tags(new String[]{"Action"})
                .steamId("76561198000000002")
                .build();

        JsonNode first = executeProceed(request, clientRequestId);
        JsonNode second = executeProceed(request, clientRequestId);

        assertThat(first.path("assistantMessageId").asText()).isEqualTo(second.path("assistantMessageId").asText());

        UUID chatId = UUID.fromString(first.path("chatId").asText());
        List<ChatMessage> messages = chatMessageRepository.findLastByChatId(chatId, 10)
                .collectList()
                .block();

        assertThat(messages).isNotNull();
        long userCount = messages.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = messages.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();

        assertThat(userCount).isEqualTo(1);
        assertThat(assistantCount).isEqualTo(1);
    }

    private JsonNode executeProceed(GameRecommendationRequest request, String clientRequestId) throws Exception {
        byte[] responseBytes = webTestClient.post()
                .uri("/api/v1/games/proceed")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Client-Request-Id", clientRequestId)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(responseBytes).isNotNull();
        return objectMapper.readTree(new String(responseBytes, StandardCharsets.UTF_8));
    }

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
}
