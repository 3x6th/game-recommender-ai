package ru.perevalov.gamerecommenderai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@Data
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @CreationTimestamp
    private void onCreate() {
        setCreatedAt(LocalDateTime.now());
        setUpdatedAt(LocalDateTime.now());
    }

    @UpdateTimestamp
    private void onUpdate() {
        setUpdatedAt(LocalDateTime.now());
    }
}