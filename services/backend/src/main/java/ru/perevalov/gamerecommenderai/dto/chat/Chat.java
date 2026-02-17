package ru.perevalov.gamerecommenderai.dto.chat;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chat {
    private UUID id;
    private ChatStatus status;
    private LocalDateTime updatedAt;
}
