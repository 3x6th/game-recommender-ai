package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;
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

    @PostMapping("/proceed")
    public Mono<ResponseEntity<GameRecommendationResponse>> getRecommendations(
            @RequestBody Mono<GameRecommendationRequest> reqMono
    ) {
        return gameRecommenderService.getGameRecommendationsWithContext(reqMono)
                .doOnNext(resp -> log.info("Returning response with {} recommendations",
                        resp.getRecommendations() != null ? resp.getRecommendations().size() : 0))
                .map(ResponseEntity::ok);
    }
}