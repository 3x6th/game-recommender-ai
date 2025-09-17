package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "response_logs")
@Entity
public class ResponseLog extends BaseEntity {
    @Column
    private Short statusCode;

    @Column(columnDefinition = "TEXT")
    private String responseMessage;

    @Column
    private LocalDateTime timestamp;

    @Column
    private Integer responseTimeMs;

    @Column(length = 100)
    private String endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id", nullable = false)
    private AiAgent aiAgent;
}