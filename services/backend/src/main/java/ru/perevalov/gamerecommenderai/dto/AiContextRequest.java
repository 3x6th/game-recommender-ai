package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContextRequest {
    private String userMessage;
    private String[] selectedTags;
    private List<String> userSteamLibrary;

    private String chatId;
    private String agentId;
    private String requestId;
    private String correlationId;


    private String language;
    private List<String> excludeGenres;
    private int maxResults;
}