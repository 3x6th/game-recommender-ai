package ru.perevalov.gamerecommenderai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("api_logs")
public class ApiLog extends BaseEntity {

    @Column("endpoint")
    private String endpoint;

    @Column("timestamp")
    private LocalDateTime timestamp;

    @Column("log_type")
    private LogType logType;

    @Column("message")
    private String message;

    @Column("status_code")
    private Short statusCode;

    @Column("response_time_ms")
    private Integer responseTimeMs;

    @Column("user_id")
    private UUID userId;

    @Column("ai_agent_id")
    private UUID aiAgentId;

}