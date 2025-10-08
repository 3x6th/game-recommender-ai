package ru.perevalov.gamerecommenderai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class PreAuthResponse {
    private String  accessToken;
    private long    accessExpiresIn; // seconds
    private String  role;
    private String  sessionId;
    private Long steamId;
}