package ru.perevalov.gamerecommenderai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.CurrentUserProfileResponse;
import ru.perevalov.gamerecommenderai.service.UserProfileService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public Mono<ResponseEntity<CurrentUserProfileResponse>> getCurrentUserProfile() {
        return userProfileService.getCurrentUserProfile().map(ResponseEntity::ok);
    }
}
