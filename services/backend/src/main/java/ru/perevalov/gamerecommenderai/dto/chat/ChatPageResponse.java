package ru.perevalov.gamerecommenderai.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChatPageResponse {

    private List<ChatDto> content;

    private int limit;

    private int offset;

    private long totalElements;
}
