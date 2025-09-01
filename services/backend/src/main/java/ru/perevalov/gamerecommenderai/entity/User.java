package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true)
    private Long steamId;

    @Enumerated(EnumType.STRING)
    private UserRole role;
}