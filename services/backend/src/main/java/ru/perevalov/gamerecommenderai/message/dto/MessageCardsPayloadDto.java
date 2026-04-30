package ru.perevalov.gamerecommenderai.message.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Payload для {@code meta.type = cards}.
 *
 * <p>Содержит единственный полиморфный список {@code items[]} (см.
 * {@link MessageItemDto}). Reasoning теперь живёт внутри списка как элемент
 * {@code kind = "reasoning"}, а не отдельным полем рядом с items.
 *
 * <p>Поля {@code text}, {@code reasoning} и {@code extra}, существовавшие
 * в старом mixed-payload, удалены — см. PCAI-141 follow-up
 * («reasoning теперь карточка, а не дубль content»).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageCardsPayloadDto {

    @JsonProperty(MessageMetaFields.CARDS_ITEMS)
    private List<MessageItemDto> items;
}
