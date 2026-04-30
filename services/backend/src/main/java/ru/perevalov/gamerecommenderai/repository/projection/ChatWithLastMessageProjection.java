package ru.perevalov.gamerecommenderai.repository.projection;

import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ChatWithLastMessageProjection {

    UUID getId();

    ChatStatus getStatus();

    LocalDateTime getUpdatedAt();

    String getLastMessagePreview();
}
