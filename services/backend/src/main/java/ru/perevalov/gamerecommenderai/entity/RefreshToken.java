package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {
    @Column(name = "token", nullable = false, unique = true, length = 2048)
    private String token;

    @Column(name = "session_id", nullable = false)
    private String sessionId;
    private String refreshToken;

    /**
     * TODO: {@link ru.perevalov.gamerecommenderai.security.AuthService#preAuthorize})
     */
    //todo delete after fix #preAuthorize
    public RefreshToken(String refreshToken, String sessionId) {
        this.refreshToken = refreshToken;
        this.sessionId = sessionId;
    }
}
