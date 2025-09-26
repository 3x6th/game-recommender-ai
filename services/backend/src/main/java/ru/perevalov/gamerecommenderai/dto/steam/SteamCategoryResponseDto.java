package ru.perevalov.gamerecommenderai.dto.steam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SteamCategoryResponseDto {
    String id;
    String description;
}
