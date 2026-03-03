package ru.perevalov.gamerecommenderai.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.perevalov.gamerecommenderai.dto.GameRecommendation;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;

/**
 * Вспомогательные операции recommendation pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineSupport {
    private final ObjectMapper objectMapper;

    /**
     * Безопасно парсит UUID из строки.
     *
     * @param raw исходная строка
     * @return UUID или {@code null}, если значение пустое или некорректное
     */
    public UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid UUID: {}", raw);
            return null;
        }
    }

    /**
     * Безопасно парсит long из строки.
     *
     * @param raw исходная строка
     * @return значение или {@code null}, если оно пустое или некорректное
     */
    public Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid long value: {}", raw);
            return null;
        }
    }

    /**
     * Извлекает теги из запроса рекомендаций.
     *
     * @param request запрос рекомендаций
     * @return список тегов или {@code null}
     */
    public List<String> extractTags(GameRecommendationRequest request) {
        if (request == null || request.getTags() == null) {
            return null;
        }
        return Arrays.asList(request.getTags());
    }

    /**
     * Строит карточки рекомендаций для meta-ответа ассистента.
     *
     * @param items список рекомендаций
     * @return список карточек, который может быть пустым
     */
    public List<MessageCardDto> buildCards(List<GameRecommendation> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(item -> new MessageCardDto(
                        null,
                        item.getTitle(),
                        item.getRating(),
                        item.getWhyRecommended(),
                        item.getGenre() != null ? List.of(item.getGenre()) : null,
                        null,
                        null
                ))
                .toList();
    }

    /**
     * Сериализует ответ рекомендаций в JSON-снапшот для meta.extra.
     *
     * @param response ответ сервиса рекомендаций
     * @return JSON-снапшот ответа
     */
    public JsonNode buildResponseSnapshot(GameRecommendationResponse response) {
        return objectMapper.valueToTree(response);
    }

    /**
     * Извлекает снапшот ответа из meta.payload.extra.
     *
     * @param meta meta-объект сообщения
     * @return восстановленный ответ или {@code null}
     */
    public GameRecommendationResponse extractSnapshot(JsonNode meta) {
        if (meta == null || !meta.isObject()) {
            return null;
        }
        JsonNode payload = meta.get(MessageMetaFields.FIELD_PAYLOAD);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode extra = payload.get(MessageMetaFields.MIXED_EXTRA);
        if (extra == null || extra.isNull()) {
            extra = payload.get(MessageMetaFields.REPLY_EXTRA);
        }
        if (extra == null || extra.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(extra, GameRecommendationResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to parse response snapshot from meta", ex);
            return null;
        }
    }
}
