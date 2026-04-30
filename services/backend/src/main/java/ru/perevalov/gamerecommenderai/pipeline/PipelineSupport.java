package ru.perevalov.gamerecommenderai.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.perevalov.gamerecommenderai.dto.GameRecommendation;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.message.dto.MessageCardDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageItemDto;
import ru.perevalov.gamerecommenderai.message.dto.MessageReasoningItemDto;

/**
 * Вспомогательные операции recommendation pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineSupport {

    /**
     * Безопасно парсит UUID из строки.
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
     */
    public List<String> extractTags(GameRecommendationRequest request) {
        if (request == null || request.getTags() == null) {
            return null;
        }
        return Arrays.asList(request.getTags());
    }

    /**
     * Собирает полиморфный список {@code meta.payload.items[]}: reasoning-блок
     * (если есть) первым элементом, далее игровые карточки.
     *
     * <p>Контракт см. {@code contracts/docs/api-contract.md} §5: {@code items[]}
     * — единственное место, где живёт визуальное содержимое ответа ассистента.
     * Маппинг {@link GameRecommendation} → {@link MessageCardDto} 1-в-1, без потерь.
     */
    public List<MessageItemDto> buildItems(
            String reasoning,
            List<GameRecommendation> recommendations
    ) {
        boolean hasReasoning = reasoning != null && !reasoning.isBlank();
        boolean hasRecs = recommendations != null && !recommendations.isEmpty();
        if (!hasReasoning && !hasRecs) {
            return Collections.emptyList();
        }

        List<MessageItemDto> items = new ArrayList<>();
        if (hasReasoning) {
            items.add(MessageReasoningItemDto.builder()
                    .text(reasoning)
                    .build());
        }
        if (hasRecs) {
            for (GameRecommendation rec : recommendations) {
                items.add(MessageCardDto.builder()
                        .title(rec.getTitle())
                        .genre(rec.getGenre())
                        .description(rec.getDescription())
                        .whyRecommended(rec.getWhyRecommended())
                        .platforms(rec.getPlatforms())
                        .rating(rec.getRating())
                        .releaseYear(rec.getReleaseYear())
                        .build());
            }
        }
        return items;
    }

}
