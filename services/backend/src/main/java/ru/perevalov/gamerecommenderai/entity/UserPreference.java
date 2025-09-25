package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "user_preferences")
public class UserPreference extends BaseEntity {
    @Column
    private Long gameId;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @JdbcTypeCode(SqlTypes.JSON)
    private String tags;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "game_name")
    private String gameName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_preferences_users_id"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id", foreignKey = @ForeignKey(name = "fk_user_preferences_ai_agents_id"))
    private AiAgent aiAgent;
}