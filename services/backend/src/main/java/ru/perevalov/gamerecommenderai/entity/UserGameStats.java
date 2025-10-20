package ru.perevalov.gamerecommenderai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("user_game_stats")
public class UserGameStats extends BaseEntity {

    @NonNull
    @Column("steam_id")
    private Long steamId;

    @Column("total_games_owned")
    private Integer totalGamesOwned;

    @Column("total_playtime_forever")
    private Integer totalPlaytimeForever;

    @Column("total_playtime_last_two_weeks")
    private Integer totalPlaytimeLastTwoWeeks;

    @Column("most_played_game_id")
    private Long mostPlayedGameId;

    @Column("most_played_game_hours")
    private Integer mostPlayedGameHours;

    @Column("last_played_game_id")
    private Long lastPlayedGameId;

    @Column("last_playtime")
    private Integer lastPlaytime;

    @Column("favorite_genre_count")
    private Integer favoriteGenreCount;

    @Column("favorite_genre_hours")
    private Integer favoriteGenreHours;

    @Column("most_played_game_name")
    private String mostPlayedGameName;

    @Column("last_played_game_name")
    private String lastPlayedGameName;

    @Column("favorite_genre")
    private String favoriteGenre;

    @Column("user_id")
    private UUID userId;

}