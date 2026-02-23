package ru.perevalov.gamerecommenderai.mapper;

import org.mapstruct.Mapper;

import ru.perevalov.gamerecommenderai.dto.chat.Chat;
import ru.perevalov.gamerecommenderai.entity.Chats;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    Chat toDto(Chats entity);

    ru.perevalov.gamerecommenderai.dto.chat.ChatMessage toDto(
            ru.perevalov.gamerecommenderai.entity.ChatMessage entity);
}
