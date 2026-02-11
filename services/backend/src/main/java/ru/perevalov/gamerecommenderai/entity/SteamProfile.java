package ru.perevalov.gamerecommenderai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("steam_profiles")
public class SteamProfile extends BaseEntity {

    @Column("steam_created")
    private Integer steamCreated;

    @Column("profile_url")
    private String profileUrl;

    @Column("profile_img")
    private String profileImg;

    @Column("user_id")
    private UUID userId;

}