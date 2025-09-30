package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "steam_apps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SteamAppEntity {
    @Id
    private Long appid;

    @Column(name = "name", nullable = false)
    private String name;
}
