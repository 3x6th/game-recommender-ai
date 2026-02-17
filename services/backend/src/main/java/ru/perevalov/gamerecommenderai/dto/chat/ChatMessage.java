package ru.perevalov.gamerecommenderai.dto.chat;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private UUID id;
    private MessageRole role;
    private String content;
    private JsonNode meta;
    private LocalDateTime createdAt;
}
