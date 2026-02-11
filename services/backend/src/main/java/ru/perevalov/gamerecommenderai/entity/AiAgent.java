package ru.perevalov.gamerecommenderai.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_agents")
public class AiAgent extends BaseEntity {

    @Column("is_active")
    private Boolean isActive;

    @Column("ai_name")
    private String aiName;

    @Column("model_name")
    private String modelName;

}