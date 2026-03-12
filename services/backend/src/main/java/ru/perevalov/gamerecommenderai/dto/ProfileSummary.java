package ru.perevalov.gamerecommenderai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProfileSummary {

    private Integer totalGamesOwned;

    private Integer totalPlaytimeHours;

    private List<GameEntry> topByPlaytime;

    private List<GameEntry> recentlyPlayed;

    private List<GameEntry> allGamesPlayed;

    @Data
    public static class GameEntry {

        private String name;

        private Integer playtimeHours;

        private Integer recentPlaytimeHours;
    }
}
