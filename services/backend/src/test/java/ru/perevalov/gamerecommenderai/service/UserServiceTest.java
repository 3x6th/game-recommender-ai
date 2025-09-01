package ru.perevalov.gamerecommenderai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.UserRepository;
import ru.perevalov.gamerecommenderai.utils.DataUtils;
import org.assertj.core.api.Assertions;

import java.util.Optional;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;


    @Test
    @DisplayName("Test find exists user by steam id functionality")
    void givenExistsUser_whenFindBySteamId_thenReturnCurrentUser() {
        // given
        User userPersisted = DataUtils.getUserPersisted(DataUtils.getMockSteamId());
        BDDMockito.given(userRepository.findBySteamId(ArgumentMatchers.anyLong()))
                .willReturn(Optional.of(userPersisted));
        // when
        User user = userService.findBySteamId(DataUtils.getMockSteamId());
        // then
        Assertions.assertThat(user.getSteamId()).isEqualTo(userPersisted.getSteamId());
        Assertions.assertThat(user.getId()).isEqualTo(userPersisted.getId());
    }

    @Test
    @DisplayName("Test find non exists user by steam id functionality")
    void givenNonExistsUser_whenFindBySteamId_thenThrowsException() {
        // given
        BDDMockito.given(userRepository.findBySteamId(ArgumentMatchers.anyLong()))
                .willThrow(GameRecommenderException.class);
        // when then
        Assertions.assertThatThrownBy(() -> userService.findBySteamId(DataUtils.getMockSteamId()))
                .isInstanceOf(GameRecommenderException.class);
    }

    @Test
    @DisplayName("Test create user by steam id functionality")
    void givenNonExistsUser_whenCreateIfNotExists_thenReturnNewUser() {
        // given
        User userPersisted = DataUtils.getUserPersisted(DataUtils.getMockSteamId());
        BDDMockito.given(userRepository.save(ArgumentMatchers.any(User.class)))
                .willReturn(userPersisted);
        // when
        User userTransient = DataUtils.getUserTransient(DataUtils.getMockSteamId());
        User createdUser = userService.createIfNotExists(userTransient.getSteamId());

        // then
        Assertions.assertThat(createdUser.getSteamId()).isEqualTo(userPersisted.getSteamId());
        Assertions.assertThat(createdUser.getId()).isEqualTo(userPersisted.getId());
    }

    @Test
    @DisplayName("Test create exists user by steam id functionality")
    void givenExistsUser_whenCreateIfNotExists_thenReturnCurrentUser() {
        // given
        User userPersisted = DataUtils.getUserPersisted(DataUtils.getMockSteamId());
        BDDMockito.given(userRepository.findBySteamId(ArgumentMatchers.anyLong()))
                .willReturn(Optional.of(userPersisted));
        // when
        User foundUser = userService.createIfNotExists(userPersisted.getSteamId());

        // then
        Assertions.assertThat(foundUser.getSteamId()).isEqualTo(userPersisted.getSteamId());
        Assertions.assertThat(foundUser.getId()).isEqualTo(userPersisted.getId());
    }
}