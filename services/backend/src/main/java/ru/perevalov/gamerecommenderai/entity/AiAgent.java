package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
public class AiAgent extends BaseEntity {
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "ai_name", length = 50, nullable = false)
    private String aiName;

    @Column(name = "model_name", length = 50)
    private String modelName;
}