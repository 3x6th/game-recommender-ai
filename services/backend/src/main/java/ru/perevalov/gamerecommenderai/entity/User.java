package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.perevalov.gamerecommenderai.security.model.UserRole;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(columnList = "steam_id", name = "idx_users_steam_id")
})
public class User extends BaseEntity {
    @Column(name = "steam_id", nullable = false, unique = true)
    private Long steamId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /**
     * TODO: {@link ru.perevalov.gamerecommenderai.service.UserService#createIfNotExists})
     */
    //todo delete after fix #createIfNotExists
    public User(Long steamId, UserRole userRole) {
        this.steamId = steamId;
        this.role = userRole;
    }
}