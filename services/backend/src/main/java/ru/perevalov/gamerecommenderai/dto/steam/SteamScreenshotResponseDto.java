package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SteamScreenshotResponseDto {
    String id;

    @JsonProperty("path_thumbnail")
    String pathThumbnail;

    @JsonProperty("path_full")
    String pathFull;
}
