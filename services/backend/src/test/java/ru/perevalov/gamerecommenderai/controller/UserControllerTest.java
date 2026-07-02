package ru.perevalov.gamerecommenderai.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.CurrentUserProfileResponse;
import ru.perevalov.gamerecommenderai.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserProfileService userProfileService;
    @InjectMocks
    private UserController userController;

    @Test
    void getCurrentUserProfile_returnsServiceResponse() {
        CurrentUserProfileResponse profile = new CurrentUserProfileResponse(
                "76561198000000000",
                "https://avatars.steamstatic.com/avatar_full.jpg",
                "https://steamcommunity.com/profiles/76561198000000000/"
        );
        when(userProfileService.getCurrentUserProfile()).thenReturn(Mono.just(profile));

        StepVerifier.create(userController.getCurrentUserProfile())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(response.getBody()).isEqualTo(profile);
                })
                .verifyComplete();
    }
}
