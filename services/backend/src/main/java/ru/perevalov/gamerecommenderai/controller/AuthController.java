package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.perevalov.gamerecommenderai.dto.PreAuthResponse;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenRequest;
import ru.perevalov.gamerecommenderai.dto.RefreshAccessTokenResponse;
import ru.perevalov.gamerecommenderai.service.AuthService;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/preAuthorize")
    public ResponseEntity<PreAuthResponse> preAuthorize() {
        PreAuthResponse resp = authService.preAuthorize();
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshAccessTokenResponse> refreshAccessToken(@RequestBody RefreshAccessTokenRequest request) {
        RefreshAccessTokenResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
