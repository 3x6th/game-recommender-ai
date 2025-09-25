package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamGameDetailsResponseDto(
        String appId,
        boolean success,
        SteamGameDataResponseDto steamGameDataResponseDto) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SteamGameDataResponseDto(
            String type,
            String name,
            @JsonProperty("steam_appid") int steamAppid,
            @JsonProperty("required_age") int requiredAge,
            @JsonProperty("is_free") boolean isFree,
            List<Integer> dlc,
            @JsonProperty("detailed_description") String detailedDescription,
            @JsonProperty("about_the_game") String aboutTheGame,
            @JsonProperty("short_description") String shortDescription,
            @JsonProperty("supported_languages") String supportedLanguages,
            @JsonProperty("header_image") String headerImage,
            @JsonProperty("capsule_image") String capsuleImage,
            @JsonProperty("capsule_imagev5") String capsuleImageV5,
            @JsonProperty("header_image_raw") String headerImageRaw,
            String website,
            @JsonIgnore @JsonProperty("pc_requirements") String pcRequirements,
            @JsonIgnore @JsonProperty("mac_requirements") String macRequirements,
            @JsonIgnore @JsonProperty("linux_requirements") String linuxRequirements,
            List<String> developers,
            List<String> publishers,
            SteamPlatformResponseDto platforms,
            List<SteamCategoryResponseDto> categories,
            List<SteamGenreResponseDto> genres,
            List<SteamScreenshotResponseDto> screenshots
    ) {
    }

    public record SteamPlatformResponseDto(
            boolean windows,
            boolean mac,
            boolean linux
    ) {
    }

    public record SteamCategoryResponseDto(
            String id,
            String description
    ) {
    }

    public record SteamGenreResponseDto(
            String id,
            String description
    ) {
    }

    public record SteamScreenshotResponseDto(
            String id,
            @JsonProperty("path_thumbnail") String pathThumbnail,
            @JsonProperty("path_full") String pathFull
    ) {
    }
}