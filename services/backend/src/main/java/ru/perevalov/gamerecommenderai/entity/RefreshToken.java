package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
public class RefreshToken extends BaseEntity {
    @Column(name = "token", nullable = false, unique = true, length = 2048)
    private String token;

    @NonNull
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @NonNull
    @Column(name = "refresh_token", nullable = false, unique = true, length = 2048)
    private String refreshToken;

}
