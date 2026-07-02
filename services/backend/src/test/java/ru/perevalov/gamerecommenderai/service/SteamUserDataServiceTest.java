package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.mapper.OwnedGamesSnapshotMapper;
import ru.perevalov.gamerecommenderai.repository.SteamProfileRepository;
import ru.perevalov.gamerecommenderai.repository.UserGameStatsRepository;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteamUserDataServiceTest {

    private static final long STEAM_ID = 76561198000000000L;

    @Mock
    private SteamService steamService;
    @Mock
    private SteamProfileRepository steamProfileRepository;
    @Mock
    private UserGameStatsRepository userGameStatsRepository;
    @Mock
    private UserDataCacheService userDataCacheService;
    @Mock
    private OwnedGamesSnapshotMapper ownedGamesSnapshotMapper;
    @Mock
    private UserGameStatsValidator userGameStatsValidator;
    @InjectMocks
    private SteamUserDataService steamUserDataService;

    @Test
    void syncSteamProfile_whenSteamReturnsPlayer_savesAvatarAndProfileUrl() {
        User user = user();
        SteamPlayerResponse.Player player = new SteamPlayerResponse.Player();
        player.setSteamId(Long.toString(STEAM_ID));
        player.setAvatarFull("https://avatars.steamstatic.com/avatar_full.jpg");
        player.setProfileUrl("https://steamcommunity.com/profiles/" + STEAM_ID + "/");
        player.setTimeCreated(1_500_000_000);

        SteamPlayerResponse.Response responseBody = new SteamPlayerResponse.Response();
        responseBody.setPlayers(List.of(player));
        SteamPlayerResponse response = new SteamPlayerResponse();
        response.setResponse(responseBody);

        when(steamService.getPlayerSummaries(Long.toString(STEAM_ID))).thenReturn(Mono.just(response));
        when(steamProfileRepository.findByUserId(user.getId())).thenReturn(Mono.empty());
        when(steamProfileRepository.save(any(SteamProfile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userDataCacheService.saveSteamProfile(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(steamUserDataService.syncSteamProfile(user)).verifyComplete();

        verify(steamProfileRepository).save(any(SteamProfile.class));
        verify(userDataCacheService).saveSteamProfile(
                org.mockito.ArgumentMatchers.eq(STEAM_ID),
                org.mockito.ArgumentMatchers.argThat(profile ->
                        player.getAvatarFull().equals(profile.getProfileImg())
                                && player.getProfileUrl().equals(profile.getProfileUrl())
                                && user.getId().equals(profile.getUserId())
                )
        );
    }

    @Test
    void syncSteamProfile_whenSteamIsUnavailable_completesWithoutBreakingLoginFlow() {
        User user = user();
        when(steamService.getPlayerSummaries(Long.toString(STEAM_ID)))
                .thenReturn(Mono.error(new RuntimeException("Steam unavailable")));

        StepVerifier.create(steamUserDataService.syncSteamProfile(user)).verifyComplete();

        verify(steamProfileRepository, never()).save(any());
        verify(userDataCacheService, never()).saveSteamProfile(any(), any());
    }

    @Test
    void syncSteamProfile_whenUserIsMissing_returnsEmpty() {
        StepVerifier.create(steamUserDataService.syncSteamProfile(null)).verifyComplete();

        verify(steamService, never()).getPlayerSummaries(any());
    }

    private User user() {
        User user = new User(STEAM_ID, UserRole.USER);
        user.setId(UUID.randomUUID());
        assertThat(user.getId()).isNotNull();
        return user;
    }
}
