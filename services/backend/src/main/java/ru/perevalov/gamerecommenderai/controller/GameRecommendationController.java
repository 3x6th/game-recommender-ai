package ru.perevalov.gamerecommenderai.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecommendationController {

    private final GameRecommenderService gameRecommenderService;

    @PostMapping("/proceed")
    public ResponseEntity<GameRecommendationResponse> getUserGames(@RequestBody GameRecommendationRequest req) {
        log.info("Received game recommendation request: {}", req);

        GameRecommendationResponse response = gameRecommenderService.getGameRecommendationsWithContext(req);

        log.info("Returning response with {} recommendations",
                response.getRecommendations() != null ? response.getRecommendations().size() : 0);

        return ResponseEntity.ok(response);
    }
} 