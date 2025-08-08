package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekRequest {
    private String model;
    private List<Message> messages;
    private Integer maxTokens;
    private Double temperature;
    private Boolean stream;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
} 