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
@Table("user_preferences")
public class UserPreference extends BaseEntity {

    @Column("game_id")
    private Long gameId;

    @Column("reasoning")
    private String reasoning;

    @Column("tags")
    private String tags;

    @Column("generated_at")
    private LocalDateTime generatedAt;

    @Column("game_name")
    private String gameName;

    @Column("user_id")
    private UUID userId;

    @Column("ai_agent_id")
    private UUID aiAgentId;

}