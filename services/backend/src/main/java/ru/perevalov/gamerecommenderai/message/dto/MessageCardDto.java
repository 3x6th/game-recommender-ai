package ru.perevalov.gamerecommenderai.message.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * DTO одной карточки рекомендации игры.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageCardDto {
    @JsonProperty(MessageMetaFields.CARD_GAME_ID)
    private String gameId;

    @JsonProperty(MessageMetaFields.CARD_TITLE)
    private String title;

    @JsonProperty(MessageMetaFields.CARD_SCORE)
    private Double score;

    @JsonProperty(MessageMetaFields.CARD_REASON)
    private String reason;

    @JsonProperty(MessageMetaFields.CARD_TAGS)
    private List<String> tags;

    @JsonProperty(MessageMetaFields.CARD_STORE_URL)
    private String storeUrl;

    @JsonProperty(MessageMetaFields.CARD_IMAGE_URL)
    private String imageUrl;
}
