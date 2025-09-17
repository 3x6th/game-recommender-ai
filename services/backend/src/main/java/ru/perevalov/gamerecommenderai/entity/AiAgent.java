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
public class AiAgent extends BaseEntity{
    @Column(nullable = false)
    private Boolean isActive;

    @Column(length = 50, nullable = false)
    private String aiName;

    @Column(length = 50)
    private String modelName;
}