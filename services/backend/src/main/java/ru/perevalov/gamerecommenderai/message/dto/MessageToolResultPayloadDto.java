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
 * Payload для {@code meta.type = tool_result}.
 *
 * <p>Ответ инструмента на парный {@link MessageToolCallPayloadDto}.
 * Сериализуется как сообщение с ролью {@code TOOL}. {@code toolCallId}
 * связывает результат с конкретным вызовом — нужен потому, что агент может
 * запустить несколько вызовов параллельно (LangChain-паттерн).
 *
 * <p>Если инструмент упал — {@code result} остаётся {@code null}, а в
 * {@code error} лежит человекочитаемое сообщение. Агент-ассистент решит,
 * как это обработать (повторить, переключиться на другой инструмент,
 * сообщить пользователю).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageToolResultPayloadDto {

    /** Имя инструмента. Дублирует поле из соответствующего {@code tool_call}. */
    @JsonProperty(MessageMetaFields.TOOL_RESULT_NAME)
    private String toolName;

    /** Корреляционный id вызова — совпадает с {@code toolCallId} из {@code tool_call}. */
    @JsonProperty(MessageMetaFields.TOOL_RESULT_CALL_ID)
    private String toolCallId;

    /** Результат работы инструмента, форма зависит от инструмента. */
    @JsonProperty(MessageMetaFields.TOOL_RESULT_RESULT)
    private JsonNode result;

    /** Текст ошибки, если инструмент упал. {@code result} в этом случае null. */
    @JsonProperty(MessageMetaFields.TOOL_RESULT_ERROR)
    private String error;
}
