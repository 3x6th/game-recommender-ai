package ru.perevalov.gamerecommenderai.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public abstract class BaseEntity implements Persistable<UUID>, Serializable {

    @Id
    private UUID id;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Флаг для отслеживания, является ли сущность новой
    @Transient
    private boolean isNewEntity = true;

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @Override
    public UUID getId() {
        if (id == null) {
            this.id = UUID.randomUUID();
        }
        return id;
    }

    // Метод для пометки сущности как существующей (после сохранения)
    public void markAsExisting() {
        this.isNewEntity = false;
    }

}
