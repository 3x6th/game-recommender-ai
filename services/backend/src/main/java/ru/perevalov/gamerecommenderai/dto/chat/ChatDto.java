package ru.perevalov.gamerecommenderai.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDto {
    private UUID chatId;
    private ChatStatus status;
    private LocalDateTime updatedAt;
    private String lastMessagePreview;
}
