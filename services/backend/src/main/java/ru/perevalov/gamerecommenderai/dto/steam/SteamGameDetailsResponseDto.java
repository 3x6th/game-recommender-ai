package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamGameDetailsResponseDto {
    private String appId;
    private boolean success;
    private SteamGameDataResponseDto steamGameDataResponseDto;
}
