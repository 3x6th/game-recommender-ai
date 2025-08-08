package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.service.DeepSeekService;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecommendationController {

    private final DeepSeekService deepSeekService;

    @PostMapping("/recommend")
    public ResponseEntity<GameRecommendationResponse> getGameRecommendation(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received game recommendation request: {}", request);
        
        String recommendation = deepSeekService.generateGameRecommendation(request.getPreferences());
        
        GameRecommendationResponse response = GameRecommendationResponse.builder()
                .recommendation(recommendation)
                .success(true)
                .build();
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<GameRecommendationResponse> chatWithAI(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received chat request: {}", request);
        
        String aiResponse = deepSeekService.generateResponse(request.getPreferences());
        
        GameRecommendationResponse response = GameRecommendationResponse.builder()
                .recommendation(aiResponse)
                .success(true)
                .build();
        
        return ResponseEntity.ok(response);
    }
} 