package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "steam_profiles")
@Entity
public class SteamProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "steam_profile_seq")
    @SequenceGenerator(name = "steam_profile_seq", sequenceName = "steam_profile_id_seq", allocationSize = 1)
    @Column(columnDefinition = "bigint")
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer steamCreated;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private String profileUrl;

    @Column
    private String profileImg;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}