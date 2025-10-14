package ru.perevalov.gamerecommenderai.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.perevalov.gamerecommenderai.config.OpenApiConfig;
import ru.perevalov.gamerecommenderai.dto.AiContextRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationRequest;
import ru.perevalov.gamerecommenderai.dto.GameRecommendationResponse;
import ru.perevalov.gamerecommenderai.security.jwt.JwtClaimKey;
import ru.perevalov.gamerecommenderai.security.jwt.JwtUtil;
import ru.perevalov.gamerecommenderai.service.GameRecommenderService;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameRecommendationController {

    private final GameRecommenderService gameRecommenderService;
    private final JwtUtil jwtUtil;

    @PostMapping("/proceed")
    public ResponseEntity<GameRecommendationResponse> getUserGames(
            @RequestBody GameRecommendationRequest req, HttpServletRequest httpServletRequest) {

        String header = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header.substring(7);
        DecodedJWT decoded = jwtUtil.decodeToken(token);
        String steamId = decoded.getClaim("steamId").toString();

        if (steamId.equals("Null claim")) {
            steamId = "";
        }
        if (req.getSteamId() == null || req.getSteamId().isBlank()) {
            req.setSteamId(steamId);
        }

        GameRecommendationResponse response = gameRecommenderService.getGameRecommendationsWithContext(req);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/recommend")
    public ResponseEntity<GameRecommendationResponse> getGameRecommendation(
            @RequestBody GameRecommendationRequest request) {
        
        log.info("Received game recommendation request: {}", request);
        
        GameRecommendationResponse response = gameRecommenderService.getGameRecommendation(
            request.getContent()
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
            request.getContent()
        );
        
        return ResponseEntity.ok(response);
    }
} 