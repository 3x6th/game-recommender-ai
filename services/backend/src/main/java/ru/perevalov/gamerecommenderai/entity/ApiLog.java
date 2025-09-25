package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "api_logs", indexes = {
        @Index(name = "idx_api_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_api_logs_ai_agent_id", columnList = "ai_agent_id")
})
public class ApiLog extends BaseEntity {

    @Column(length = 100, nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private LogType logType;

    @Lob
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "status_code")
    private Short statusCode;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_api_logs_users_id"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id", nullable = false, foreignKey = @ForeignKey(name = "fk_api_logs_ai_agents_id"))
    private AiAgent aiAgent;

}