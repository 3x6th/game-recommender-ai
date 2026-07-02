package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.CurrentUserProfileResponse;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.SteamProfileRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final long STEAM_ID = 76561198000000000L;

    @Mock
    private RequestContextResolver requestContextResolver;
    @Mock
    private SteamProfileRepository steamProfileRepository;
    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void getCurrentUserProfile_whenProfileExists_returnsPublicProfileData() {
        UUID userId = UUID.randomUUID();
        RequestContext context = RequestContext.forUser(userId, STEAM_ID, null);
        SteamProfile profile = new SteamProfile();
        profile.setUserId(userId);
        profile.setProfileImg("https://avatars.steamstatic.com/avatar_full.jpg");
        profile.setProfileUrl("https://steamcommunity.com/profiles/" + STEAM_ID + "/");

        when(requestContextResolver.resolve()).thenReturn(Mono.just(context));
        when(steamProfileRepository.findByUserId(userId)).thenReturn(Mono.just(profile));

        StepVerifier.create(userProfileService.getCurrentUserProfile())
                .assertNext(response -> assertThat(response).isEqualTo(new CurrentUserProfileResponse(
                        Long.toString(STEAM_ID),
                        profile.getProfileImg(),
                        profile.getProfileUrl()
                )))
                .verifyComplete();
    }

    @Test
    void getCurrentUserProfile_whenProfileHasNotBeenSynced_returnsNullableUrls() {
        UUID userId = UUID.randomUUID();
        RequestContext context = RequestContext.forUser(userId, STEAM_ID, null);

        when(requestContextResolver.resolve()).thenReturn(Mono.just(context));
        when(steamProfileRepository.findByUserId(userId)).thenReturn(Mono.empty());

        StepVerifier.create(userProfileService.getCurrentUserProfile())
                .assertNext(response -> {
                    assertThat(response.steamId()).isEqualTo(Long.toString(STEAM_ID));
                    assertThat(response.avatarUrl()).isNull();
                    assertThat(response.profileUrl()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void getCurrentUserProfile_whenCurrentIdentityIsGuest_returnsAuthenticationError() {
        when(requestContextResolver.resolve()).thenReturn(Mono.just(RequestContext.forGuest("guest-session", null)));

        StepVerifier.create(userProfileService.getCurrentUserProfile())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GameRecommenderException.class);
                    assertThat(((GameRecommenderException) error).getErrorType())
                            .isEqualTo(ErrorType.AUTHENTICATION_REQUIRED);
                })
                .verify();

        verify(steamProfileRepository, never()).findByUserId(org.mockito.ArgumentMatchers.any());
    }
}
