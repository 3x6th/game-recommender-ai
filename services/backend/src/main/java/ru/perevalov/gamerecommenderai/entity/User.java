package ru.perevalov.gamerecommenderai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.perevalov.gamerecommenderai.security.model.UserRole;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Table("users")
public class User extends BaseEntity {

    @NonNull
    @Column("steam_id")
    private Long steamId;

    @Column("is_active")
    private boolean isActive;

    @NonNull
    @Column("role")
    private UserRole role;

}