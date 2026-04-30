package ru.perevalov.gamerecommenderai.dto.chat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ POST /api/v1/games/proceed.
 *
 * <p>Контракт зафиксирован в {@code contracts/docs/api-contract.md} §1.
 * Содержит идентификатор чата и список ассистентских сообщений, добавленных
 * за этот ход (обычно одно). USER-сообщение в ответе не дублируется — FE
 * уже знает свой текст.
 *
 * <p>Формат каждого элемента {@link ChatMessageDto} 1-в-1 совпадает с тем,
 * что отдаёт {@code GET /api/v1/chats/{chatId}/messages}, чтобы FE имел
 * единый рендер по {@code meta.type} в обоих эндпоинтах.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProceedResponse {
    private UUID chatId;
    private List<ChatMessageDto> messages;
}
