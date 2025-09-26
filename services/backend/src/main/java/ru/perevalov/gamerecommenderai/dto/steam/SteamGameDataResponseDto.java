package ru.perevalov.gamerecommenderai.dto.steam;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamGameDataResponseDto {
    private String type;

    private String name;

    @JsonProperty("steam_appid")
    private int steamAppid;

    @JsonProperty("required_age")
    private int requiredAge;

    @JsonProperty("is_free")
    private boolean isFree;

    private List<Integer> dlc;

    @JsonProperty("detailed_description")
    private String detailedDescription;

    @JsonProperty("about_the_game")
    private String aboutTheGame;

    @JsonProperty("short_description")
    private String shortDescription;

    @JsonProperty("supported_languages")
    private String supportedLanguages;

    @JsonProperty("header_image")
    private String headerImage;

    @JsonProperty("capsule_image")
    private String capsuleImage;

    @JsonProperty("capsule_imagev5")
    private String capsuleImageV5;

    @JsonProperty("header_image_raw")
    private String headerImageRaw;

    private String website;

    @JsonIgnore
    @JsonProperty("pc_requirements")
    private String pcRequirements;

    @JsonIgnore
    @JsonProperty("mac_requirements")
    private String macRequirements;

    @JsonIgnore
    @JsonProperty("linux_requirements")
    private String linuxRequirements;

    private List<String> developers;

    private List<String> publishers;

    private SteamPlatformResponseDto platforms;

    private List<SteamCategoryResponseDto> categories;

    private List<SteamGenreResponseDto> genres;

    private List<SteamScreenshotResponseDto> screenshots;

}
