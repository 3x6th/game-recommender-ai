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
@Table(name = "request_logs")
@Entity
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "request_log_seq")
    @SequenceGenerator(name = "request_log_seq", sequenceName = "request_log_id_seq", allocationSize = 10)
    @Column(columnDefinition = "bigint")
    private Long id;

    @Column
    private Short statusCode;

    @Column(columnDefinition = "TEXT")
    private String requestMessage;

    @Column
    private LocalDateTime timestamp;

    @Column(length = 100)
    private String endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id", nullable = false)
    private AiAgent aiAgent;
}