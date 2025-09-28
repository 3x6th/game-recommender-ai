package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {
    @Column(name = "token", nullable = false, unique = true, length = 2048)
    private String token;

    @NonNull
    @Column(name = "session_id", nullable = false)
    private String sessionId;

}