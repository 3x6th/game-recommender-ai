package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Универсальная DTO-оболочка для meta: schemaVersion/type/payload/trace.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageMetaDto<T> {
    @JsonProperty(MessageMetaFields.FIELD_SCHEMA_VERSION)
    private int schemaVersion;

    @JsonProperty(MessageMetaFields.FIELD_TYPE)
    private String type;

    @JsonProperty(MessageMetaFields.FIELD_PAYLOAD)
    private T payload;

    @JsonProperty(MessageMetaFields.FIELD_TRACE)
    private MessageTraceDto trace;
}
