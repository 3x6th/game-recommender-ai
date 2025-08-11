package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.service.GrpcGameRecommenderService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecommendationController {

    private final GrpcGameRecommenderService grpcGameRecommenderService;

    @PostMapping("/recommend")
    public ResponseEntity<GameRecommendationResponse> getGameRecommendation(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received game recommendation request: {}", request);
        
        GameRecommendationResponse response = grpcGameRecommenderService.getGameRecommendation(request.getPreferences());
        
        log.info("Returning response with {} recommendations", 
                response.getRecommendations() != null ? response.getRecommendations().size() : 0);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<GameRecommendationResponse> chatWithAI(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received chat request: {}", request);
        
        GameRecommendationResponse response = grpcGameRecommenderService.chatWithAI(request.getPreferences());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<String> testGrpcConnection() {
        log.info("Testing gRPC connection");
        try {
            GameRecommendationResponse response = grpcGameRecommenderService.getGameRecommendation("action games");
            return ResponseEntity.ok("gRPC connection successful: " + response.getRecommendation());
        } catch (Exception e) {
            log.error("gRPC connection failed", e);
            return ResponseEntity.status(500).body("gRPC connection failed: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Object> healthCheck() {
        log.info("Health check requested");
        try {
            // Test gRPC connection with a simple request
            GameRecommendationResponse response = grpcGameRecommenderService.getGameRecommendation("test");
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "grpc_connection", "ok",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "unhealthy",
                "grpc_connection", "failed",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
} 