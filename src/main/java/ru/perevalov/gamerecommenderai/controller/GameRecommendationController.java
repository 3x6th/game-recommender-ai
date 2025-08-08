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
        
        try {
            String recommendation = deepSeekService.generateGameRecommendation(request.getPreferences());
            
            GameRecommendationResponse response = GameRecommendationResponse.builder()
                    .recommendation(recommendation)
                    .success(true)
                    .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing game recommendation request", e);
            
            GameRecommendationResponse response = GameRecommendationResponse.builder()
                    .recommendation("Извините, произошла ошибка при обработке запроса.")
                    .success(false)
                    .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<GameRecommendationResponse> chatWithAI(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received chat request: {}", request);
        
        try {
            String response = deepSeekService.generateResponse(request.getPreferences());
            
            GameRecommendationResponse gameResponse = GameRecommendationResponse.builder()
                    .recommendation(response)
                    .success(true)
                    .build();
            
            return ResponseEntity.ok(gameResponse);
            
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            
            GameRecommendationResponse response = GameRecommendationResponse.builder()
                    .recommendation("Извините, произошла ошибка при обработке запроса.")
                    .success(false)
                    .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Game Recommender AI is running!");
    }
} 