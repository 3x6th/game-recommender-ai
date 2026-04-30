package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Текстовый элемент {@code meta.payload.items[]} с {@code kind = "text"}.
 *
 * <p>Нарративный блок ассистента в составе многоэлементного ответа
 * (например, после {@code tool_result} агент формирует финальный текст +
 * карточки в одном сообщении). Отличается от {@code reasoning} семантически:
 * {@code reasoning} — это метакомментарий «почему я выбрал этот набор»,
 * а {@code text} — сам ответ на вопрос пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MessageTextItemDto implements MessageItemDto {

    @JsonProperty(MessageMetaFields.ITEM_TEXT)
    private String text;
}
