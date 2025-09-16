package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_game_stats")
@Entity
public class UserGameStats {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_game_stats_seq")
    @SequenceGenerator(name = "user_game_stats_seq", sequenceName = "user_game_stats_id_seq", allocationSize = 1)
    @Column(columnDefinition = "bigint")
    private Long id;

    @Column(nullable = false)
    private Long steamId;

    @Column
    private Integer totalGamesOwned;

    @Column
    private Integer totalPlaytimeForever;

    @Column
    private Integer totalPlaytimeLastTwoWeeks;

    @Column(columnDefinition = "bigint")
    private Long mostPlayedGameId;

    @Column
    private Integer mostPlayedGameHours;

    @Column(columnDefinition = "bigint")
    private Long lastPlayedGameId;

    @Column
    private Integer lastPlaytime;

    @Column
    private Integer favoriteGenreCount;

    @Column
    private Integer favoriteGenreHours;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private String mostPlayedGameName;

    @Column
    private String lastPlayedGameName;

    @Column(length = 100)
    private String favoriteGenre;
}