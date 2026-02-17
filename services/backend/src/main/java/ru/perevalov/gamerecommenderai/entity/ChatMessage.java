package ru.perevalov.gamerecommenderai.entity;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.perevalov.gamerecommenderai.entity.enums.MessageRole;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_messages")
public class ChatMessage extends BaseEntity {

    @Column("chat_id")
    private UUID chatId;

    @Column("role")
    private MessageRole role;

    @Column("content")
    private String content;

    @Column("meta")
    private JsonNode meta;

    @Column("client_request_id")
    private UUID clientRequestId;
}
