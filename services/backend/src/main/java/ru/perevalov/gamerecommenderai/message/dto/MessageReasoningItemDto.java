package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Reasoning-элемент {@code meta.payload.items[]} с {@code kind = "reasoning"}.
 *
 * <p>Содержит общий разбор от LLM «почему был выбран именно такой набор
 * карточек» (отдельно от {@code whyRecommended} в каждой игровой карточке).
 * FE рендерит как блок-объяснение над списком игр.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MessageReasoningItemDto implements MessageItemDto {

    /** Текст объяснения. */
    @JsonProperty(MessageMetaFields.ITEM_TEXT)
    private String text;
}
