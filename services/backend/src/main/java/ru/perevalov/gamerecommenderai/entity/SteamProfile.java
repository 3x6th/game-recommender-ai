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
public class SteamProfile extends BaseEntity {
    @Column(nullable = false, unique = true)
    private Integer steamCreated;

    @Column
    private String profileUrl;

    @Column
    private String profileImg;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}