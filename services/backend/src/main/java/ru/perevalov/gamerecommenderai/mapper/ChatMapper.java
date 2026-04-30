package ru.perevalov.gamerecommenderai.mapper;

import java.time.Instant;
import java.time.LocalDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.perevalov.gamerecommenderai.dto.chat.ChatDto;
import ru.perevalov.gamerecommenderai.dto.chat.ChatMessageDto;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;
import ru.perevalov.gamerecommenderai.util.TimeHelper;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(source = "id", target = "chatId")
    ChatDto toDto(ChatWithLastMessageProjection projection);

    @Mapping(source = "id", target = "messageId")
    ChatMessageDto toDto(ChatMessage entity);

    default Instant map(LocalDateTime value) {
        return TimeHelper.toSystemTimezoneInstant(value);
    }
}
