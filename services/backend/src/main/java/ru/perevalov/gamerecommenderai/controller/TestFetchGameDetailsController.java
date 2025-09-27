package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.perevalov.gamerecommenderai.client.SteamStoreClient;
import ru.perevalov.gamerecommenderai.dto.steam.SteamGameDetailsResponseDto;

@Slf4j
@RestController
@RequestMapping("/api/steam")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestFetchGameDetailsController {

    private final SteamStoreClient steamStoreClient;

    @GetMapping()
    public ResponseEntity<SteamGameDetailsResponseDto> getGameDetails(@RequestParam("id") String id) {
        SteamGameDetailsResponseDto response = steamStoreClient.fetchGameDetails(id);
        return ResponseEntity.ok(response);
    }
}