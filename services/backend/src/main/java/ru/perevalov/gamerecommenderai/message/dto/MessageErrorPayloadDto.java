package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Payload для meta.type = error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageErrorPayloadDto {
    @JsonProperty(MessageMetaFields.ERROR_CODE)
    private String code;

    @JsonProperty(MessageMetaFields.ERROR_MESSAGE)
    private String message;

    @JsonProperty(MessageMetaFields.ERROR_RETRYABLE)
    private boolean retryable;
}
