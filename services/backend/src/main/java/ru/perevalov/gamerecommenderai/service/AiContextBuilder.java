package ru.perevalov.gamerecommenderai.service;


import ru.perevalov.gamerecommenderai.dto.AiContextRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AiContextBuilder {
    private String userMessage;
    private String[] selectedTags;
    private final String defaultLanguage;
    private String chatId;
    private String agentId;
    private String requestId;
    private String correlationId;
    private String language;
    private List<String> excludeGenres;
    private int maxResults;
    private final int defaultMaxResults;
    private List<String> profileSummary;

    public AiContextBuilder(String defaultLanguage, int defaultMaxResults) {
        this.defaultLanguage = defaultLanguage;
        this.defaultMaxResults = defaultMaxResults;
    }

    public AiContextBuilder userMessage(String val) { this.userMessage = val; return this; }
    public AiContextBuilder selectedTags(String[] val) { this.selectedTags = val; return this; }
    public AiContextBuilder profileSummary(List<String> val) {this.profileSummary = val; return this;}
    public AiContextBuilder chatId(String val) { this.chatId = val; return this; }
    public AiContextBuilder agentId(String val) { this.agentId = val; return this; }
    public AiContextBuilder reqId(String val) { this.requestId = val; return this; }
    public AiContextBuilder corrId(String val) { this.correlationId = val; return this; }
    public AiContextBuilder excludeGenres(List<String> val) { this.excludeGenres = val; return this; };

    public AiContextBuilder language(String val) {
        this.language = val;
        return this;
    }
    public AiContextBuilder maxResults(int val) {
        this.maxResults = val;
        return this;
    }

    AiContextRequest build(){
        AiContextRequest req = new AiContextRequest();

        req.setUserMessage(this.userMessage != null ? this.userMessage : "");
        req.setSelectedTags(this.selectedTags != null ? this.selectedTags : new String[0]);
        req.setProfileSummary(this.profileSummary != null ? "User plays: " + String.join(", ", this.profileSummary) : "");
        req.setRequestId(this.requestId != null ? this.requestId : UUID.randomUUID().toString());
        req.setCorrelationId(this.correlationId != null ? this.correlationId : UUID.randomUUID().toString());
        req.setChatId(this.chatId);
        req.setAgentId(this.agentId);
        req.setLanguage(this.language != null ? this.language : defaultLanguage);
        req.setExcludeGenres(this.excludeGenres != null ? this.excludeGenres :Collections.emptyList());
        req.setMaxResults(this.maxResults > 0 ? this.maxResults : defaultMaxResults);

        return req;



    }





}