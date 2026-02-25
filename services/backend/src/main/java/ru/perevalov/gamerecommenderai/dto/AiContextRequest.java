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
    private String requestId;
    private String userMessage;
    private String[] selectedTags;
    private List<String> gameLibrary;
    private Integer maxResults;
}
