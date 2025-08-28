package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecommendationController {

    private final GameRecommenderService gameRecommenderService;

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        log.info("Test endpoint called");
        return ResponseEntity.ok("Backend is running! gRPC connection test endpoint.");
    }

    @PostMapping("/recommend")
    public ResponseEntity<GameRecommendationResponse> getGameRecommendation(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received game recommendation request: {}", request);
        
        GameRecommendationResponse response = gameRecommenderService.getGameRecommendation(
            request.getPreferences()
        );
        
        log.info("Returning response with {} recommendations", 
                response.getRecommendations() != null ? response.getRecommendations().size() : 0);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<GameRecommendationResponse> chatWithAI(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received chat request: {}", request);
        
        GameRecommendationResponse response = gameRecommenderService.chatWithAI(
            request.getPreferences()
        );
        
        return ResponseEntity.ok(response);
    }
} 