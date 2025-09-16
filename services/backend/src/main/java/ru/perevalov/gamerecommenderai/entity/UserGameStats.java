package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_game_stats")
@Entity
public class UserGameStats extends BaseEntity {
    @Column(nullable = false)
    private Long steamId;

    @Column(name = "total_games_owned")
    private Integer totalGamesOwned;

    @Column(name = "total_playtime_forever")
    private Integer totalPlaytimeForever;

    @Column(name = "total_playtime_last_two_weeks")
    private Integer totalPlaytimeLastTwoWeeks;

    @Column(name = "most_played_game_id")
    private Long mostPlayedGameId;

    @Column(name = "most_played_game_hours")
    private Integer mostPlayedGameHours;

    @Column(name = "last_played_game_id")
    private Long lastPlayedGameId;

    @Column(name = "last_playtime")
    private Integer lastPlaytime;

    @Column(name = "favorite_genre_count")
    private Integer favoriteGenreCount;

    @Column(name = "favorite_genre_hours")
    private Integer favoriteGenreHours;

    @Column(name = "most_played_game_name")
    private String mostPlayedGameName;

    @Column(name = "last_played_game_name")
    private String lastPlayedGameName;

    @Column(name = "favorite_genre", length = 100)
    private String favoriteGenre;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_game_stats_users_id"))
    private User user;
}