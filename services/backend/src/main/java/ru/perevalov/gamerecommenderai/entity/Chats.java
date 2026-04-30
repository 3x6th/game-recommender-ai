package ru.perevalov.gamerecommenderai.entity;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.perevalov.gamerecommenderai.entity.enums.ChatStatus;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("chats")
public class Chats extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    @Column("session_id")
    private String sessionId;

    @Column("ai_agent_id")
    private UUID aiAgentId;

    @Column("status")
    private ChatStatus status;
}
