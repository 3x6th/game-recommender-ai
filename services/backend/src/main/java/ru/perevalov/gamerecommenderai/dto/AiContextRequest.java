package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContextRequest {
    private String userMessage;
    private String[] selectedTags;
    private SteamOwnedGamesResponse gameLibrary;
}
