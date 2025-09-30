package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamAppResponseDto(
        @JsonProperty("applist") AppList appList) {

    public record AppList(
            @JsonProperty("apps") List<App> apps
    ) {

        public record App(
                @JsonProperty("appid") Long appid,
                @JsonProperty("name") String name
        ) {
        }
    }
}