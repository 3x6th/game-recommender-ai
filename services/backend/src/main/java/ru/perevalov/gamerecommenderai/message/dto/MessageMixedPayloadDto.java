package ru.perevalov.gamerecommenderai.message.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Payload для meta.type = mixed (текст + карточки + extra).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageMixedPayloadDto {
    @JsonProperty(MessageMetaFields.MIXED_TEXT)
    private String text;

    @JsonProperty(MessageMetaFields.MIXED_ITEMS)
    private List<MessageCardDto> items;

    @JsonProperty(MessageMetaFields.MIXED_EXTRA)
    private JsonNode extra;
}
