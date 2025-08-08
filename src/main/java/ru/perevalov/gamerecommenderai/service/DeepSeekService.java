package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.perevalov.gamerecommenderai.config.DeepSeekConfig;
import ru.perevalov.gamerecommenderai.dto.DeepSeekRequest;
import ru.perevalov.gamerecommenderai.dto.DeepSeekResponse;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    private final DeepSeekConfig config;
    private final WebClient webClient;

    public String generateResponse(String userMessage) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(config.getModel())
                .messages(List.of(
                        DeepSeekRequest.Message.builder()
                                .role("user")
                                .content(userMessage)
                                .build()
                ))
                .maxTokens(config.getMaxTokens())
                .temperature(config.getTemperature())
                .stream(false)
                .build();

        log.debug("Sending request to DeepSeek: {}", request);

        DeepSeekResponse response = webClient.post()
                .uri(config.getApi().getUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApi().getKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .block();

        if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
            String content = response.getChoices().getFirst().getMessage().getContent();
            log.debug("Received response from DeepSeek: {}", content);
            return content;
        } else {
            throw new GameRecommenderException(
                "Получен пустой или некорректный ответ от DeepSeek API",
                "DEEPSEEK_EMPTY_RESPONSE",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    public String generateGameRecommendation(String userPreferences) {
        String prompt = String.format("""
                Ты - эксперт по видеоиграм. Пользователь ищет игру со следующими предпочтениями: %s
                
                Пожалуйста, порекомендуй 3-5 игр, которые подходят под эти предпочтения.
                Для каждой игры укажи:
                - Название
                - Жанр
                - Краткое описание
                - Почему она подходит под запрос пользователя
                
                Ответ дай на русском языке в формате списка.
                """, userPreferences);

        return generateResponse(prompt);
    }
} 