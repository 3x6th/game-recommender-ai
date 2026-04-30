package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.message.MessageMetaFields;

/**
 * Payload для {@code meta.type = tool_call}.
 *
 * <p>Сообщение от ассистента-агента, инициирующее вызов инструмента
 * (Steam-поиск, similar games, RAG-запрос и т.п.). Парный {@code tool_result}
 * с тем же {@code toolCallId} приходит сообщением с ролью {@code TOOL}.
 *
 * <p>Сейчас сами инструменты ещё не реализованы (релиз с LangChain/tools идёт
 * следующим), но контракт фиксируется здесь, чтобы FE мог рендерить «calling
 * <tool>...» и storage был совместим с момента включения.
 *
 * @see MessageToolResultPayloadDto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageToolCallPayloadDto {

    /** Имя инструмента, например {@code "steam_search"}. */
    @JsonProperty(MessageMetaFields.TOOL_CALL_NAME)
    private String toolName;

    /** Аргументы вызова, форма зависит от инструмента. */
    @JsonProperty(MessageMetaFields.TOOL_CALL_ARGS)
    private JsonNode args;

    /**
     * Корреляционный id вызова. Должен совпадать с {@code toolCallId}
     * соответствующего {@link MessageToolResultPayloadDto}.
     */
    @JsonProperty(MessageMetaFields.TOOL_CALL_ID)
    private String toolCallId;
}
