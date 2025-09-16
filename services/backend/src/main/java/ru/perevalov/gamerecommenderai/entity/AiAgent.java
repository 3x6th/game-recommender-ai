package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_agents")
@Entity
public class AiAgent {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_agent_seq")
    @SequenceGenerator(name = "ai_agent_seq", sequenceName = "ai_agent_id_seq", allocationSize = 1)
    @Column(columnDefinition = "bigint")
    private Long id;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(length = 50, nullable = false)
    private String aiName;

    @Column(length = 50)
    private String modelName;
}