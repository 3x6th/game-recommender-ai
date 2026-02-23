package ru.perevalov.gamerecommenderai.message.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Payload для meta.type = reply.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageReplyPayloadDto {
    /**
     * Текст ответа.
     */
    @JsonProperty(MessageMetaFields.REPLY_TEXT)
    private String text;

    /**
     * Опциональный clientRequestId для дедупликации и трассировки.
     */
    @JsonProperty(MessageMetaFields.REPLY_CLIENT_REQUEST_ID)
    private UUID clientRequestId;

    /**
     * Опциональные теги, выбранные пользователем.
     */
    @JsonProperty(MessageMetaFields.REPLY_TAGS)
    private List<String> tags;

    /**
     * Опциональный дополнительный payload клиентских метаданных.
     */
    @JsonProperty(MessageMetaFields.REPLY_EXTRA)
    private JsonNode extra;
}
