package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(columnList = "steam_id", name = "idx_users_steam_id")
})
public class User extends BaseEntity{
    @Column(nullable = false, unique = true)
    private Long steamId;

    @Column(nullable = false)
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /**
     * TODO: {@link ru.perevalov.gamerecommenderai.service.UserService#createIfNotExists})
     */
    //todo delete after fix #createIfNotExists
    public User(Long steamId, UserRole userRole) {
    }
}