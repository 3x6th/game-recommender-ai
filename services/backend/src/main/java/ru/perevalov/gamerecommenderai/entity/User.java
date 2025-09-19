package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.perevalov.gamerecommenderai.security.model.UserRole;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(columnList = "steam_id", name = "idx_users_steam_id")
})
public class User extends BaseEntity {
    @NonNull
    @Column(name = "steam_id", nullable = false, unique = true)
    private Long steamId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @NonNull
    @Enumerated(EnumType.STRING)
    private UserRole role;

}