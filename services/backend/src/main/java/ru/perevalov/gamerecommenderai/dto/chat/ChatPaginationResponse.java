package ru.perevalov.gamerecommenderai.dto.chat;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatPaginationResponse {
    private UUID id;
    private ChatStatus status;
    private LocalDateTime updatedAt;
    private String lastMessagePreview;
}
