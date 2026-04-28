package ru.perevalov.gamerecommenderai.message.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Канонический DTO карточки игры внутри meta.payload.items[].
 *
 * <p>Контракт зафиксирован в {@code contracts/docs/api-contract.md} §5. Содержит
 * только те поля, которые приходят от LLM. Stim-специфичные обогащения
 * (gameId/storeUrl/imageUrl) намеренно отсутствуют (см. PCAI-148 — Won't Do).
 *
 * <p>FE по-обязательно умеет fallback на родительский content при незнакомых
 * полях, поэтому добавление новых полей в этот DTO не ломает совместимость.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageCardDto {

    /** Display name. */
    @JsonProperty(MessageMetaFields.CARD_TITLE)
    private String title;

    /** Comma-separated жанры, e.g. "Racing, Open World". */
    @JsonProperty(MessageMetaFields.CARD_GENRE)
    private String genre;

    /** Что за игра. */
    @JsonProperty(MessageMetaFields.CARD_DESCRIPTION)
    private String description;

    /** Почему именно эта карточка для этого юзера. */
    @JsonProperty(MessageMetaFields.CARD_WHY_RECOMMENDED)
    private String whyRecommended;

    /** Платформы, e.g. ["PC", "Xbox Series X/S"]. */
    @JsonProperty(MessageMetaFields.CARD_PLATFORMS)
    private List<String> platforms;

    /** Рейтинг 0..10 (опц., LLM-style scale). */
    @JsonProperty(MessageMetaFields.CARD_RATING)
    private Double rating;

    /** Год релиза строкой (опц., LLM может вернуть "TBD"). */
    @JsonProperty(MessageMetaFields.CARD_RELEASE_YEAR)
    private String releaseYear;

    /** Свободные теги (опц.), e.g. ["Co-op","Short sessions"]. */
    @JsonProperty(MessageMetaFields.CARD_TAGS)
    private List<String> tags;

    /** Насколько карточка подходит под запрос юзера, 0..1 (опц.). */
    @JsonProperty(MessageMetaFields.CARD_MATCH_SCORE)
    private Double matchScore;
}
