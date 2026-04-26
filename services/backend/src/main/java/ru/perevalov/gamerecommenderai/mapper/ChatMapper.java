package ru.perevalov.gamerecommenderai.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.perevalov.gamerecommenderai.dto.chat.ChatDto;
import ru.perevalov.gamerecommenderai.dto.chat.ChatMessageDto;
import ru.perevalov.gamerecommenderai.entity.ChatMessage;
import ru.perevalov.gamerecommenderai.repository.projection.ChatWithLastMessageProjection;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(source = "id", target = "chatId")
    ChatDto toDto(ChatWithLastMessageProjection projection);

    @Mapping(source = "id", target = "messageId")
    ChatMessageDto toDto(ChatMessage entity);
}
