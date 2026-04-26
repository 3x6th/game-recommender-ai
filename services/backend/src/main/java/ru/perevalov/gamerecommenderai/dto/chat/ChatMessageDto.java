package ru.perevalov.gamerecommenderai.dto.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private UUID messageId;
    private MessageRole role;
    private String content;
    private JsonNode meta;
    private LocalDateTime createdAt;
}
