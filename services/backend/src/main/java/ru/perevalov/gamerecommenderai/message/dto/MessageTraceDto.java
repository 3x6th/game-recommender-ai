package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * DTO для трассировки: requestId/runId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageTraceDto {
    @JsonProperty(MessageMetaFields.TRACE_REQUEST_ID)
    private String requestId;

    @JsonProperty(MessageMetaFields.TRACE_RUN_ID)
    private String runId;
}
