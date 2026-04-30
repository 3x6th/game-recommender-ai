package ru.perevalov.gamerecommenderai.message.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Полиморфный элемент {@code meta.payload.items[]} ответа ассистента.
 *
 * <p>Дискриминатор — поле {@code kind}:
 * <ul>
 *     <li>{@code "game"} — карточка игровой рекомендации ({@link MessageCardDto});</li>
 *     <li>{@code "reasoning"} — метакомментарий «почему именно этот набор»
 *         ({@link MessageReasoningItemDto});</li>
 *     <li>{@code "text"} — нарративный текстовый блок ассистента
 *         ({@link MessageTextItemDto}). Используется в составных ответах,
 *         когда после tool-цикла агент пишет текст + карточки в одном сообщении.</li>
 * </ul>
 *
 * <p>FE рендерит массив сверху вниз. Контракт см. в
 * {@code contracts/docs/api-contract.md} §5.
 *
 * <p>Расширение через новый {@code kind} безопасно: FE обязан игнорировать
 * незнакомые элементы. Зарезервированные для следующих релизов: {@code profile_review},
 * {@code clarifying_question}, {@code quick_replies}.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "kind",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageCardDto.class, name = "game"),
        @JsonSubTypes.Type(value = MessageReasoningItemDto.class, name = "reasoning"),
        @JsonSubTypes.Type(value = MessageTextItemDto.class, name = "text")
})
public sealed interface MessageItemDto
        permits MessageCardDto, MessageReasoningItemDto, MessageTextItemDto {
}
