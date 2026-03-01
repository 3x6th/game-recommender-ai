package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.dto.steam.SteamPlayerResponse;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;
import ru.perevalov.gamerecommenderai.entity.User;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.mapper.OwnedGamesSnapshotMapper;
import ru.perevalov.gamerecommenderai.repository.SteamProfileRepository;
import ru.perevalov.gamerecommenderai.repository.UserGameStatsRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamUserDataService {

    private final SteamService steamService;
    private final SteamProfileRepository steamProfileRepository;
    private final UserGameStatsRepository userGameStatsRepository;
    private final UserDataCacheService userDataCacheService;
    private final OwnedGamesSnapshotMapper ownedGamesSnapshotMapper;
    private final UserGameStatsValidator userGameStatsValidator;

    /**
     * Fetches user profile and game stats from Steam API and stores them in DB + Redis cache.
     * <p>
     * Designed to be called right after successful Steam OpenID auth and from background refresh jobs.
     */
    public Mono<Void> syncUserData(User user) {
        if (user == null || user.getSteamId() == null) {
            return Mono.empty();
        }

        Long steamId = user.getSteamId();
        UUID userId = user.getId();

        Mono<Void> profile = syncSteamProfile(steamId, userId);
        Mono<Void> stats = syncUserGameStats(steamId, userId);

        return Mono.whenDelayError(profile, stats).then();
    }

    private Mono<Void> syncSteamProfile(Long steamId, UUID userId) {
        return steamService.getPlayerSummaries(String.valueOf(steamId))
                .flatMap(resp -> {
                    SteamPlayerResponse.Player player = firstPlayerOrNull(resp);
                    if (player == null) {
                        log.warn("Steam player summaries is empty for steamId={}", steamId);
                        return Mono.empty();
                    }

                    Integer steamCreated = player.getTimeCreated();
                    if (steamCreated == null) {
                        steamCreated = Math.toIntExact(Instant.now().getEpochSecond());
                        log.warn("Steam timecreated is missing for steamId={}, using fallback={}", steamId, steamCreated);
                    }

                    SteamProfile profile = new SteamProfile();
                    profile.setUserId(userId);
                    profile.setSteamCreated(steamCreated);
                    profile.setProfileUrl(player.getProfileUrl());
                    profile.setProfileImg(player.getAvatarFull());

                    return upsertSteamProfile(steamId, userId, profile);
                })
                .flatMap(saved -> userDataCacheService.saveSteamProfile(steamId, saved).thenReturn(saved))
                .doOnSuccess(saved -> log.info("Steam profile synced for steamId={}, userId={}", steamId, userId))
                .then()
                .onErrorResume(e -> {
                    log.error("Failed to sync Steam profile for steamId={}, userId={}", steamId, userId, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> syncUserGameStats(Long steamId, UUID userId) {
        return steamService.getOwnedGames(String.valueOf(steamId), true, true)
                .map(resp -> buildStats(steamId, userId, resp))
                .doOnNext(userGameStatsValidator::validate)
                .flatMap(stats -> upsertUserGameStats(userId, stats))
                .flatMap(saved -> userDataCacheService.saveUserGameStats(steamId, saved).thenReturn(saved))
                .doOnSuccess(saved -> log.info("User game stats synced for steamId={}, userId={}", steamId, userId))
                .then()
                .onErrorResume(e -> {
                    log.error("Failed to sync user game stats for steamId={}, userId={}", steamId, userId, e);
                    return Mono.empty();
                });
    }

    private Mono<SteamProfile> upsertSteamProfile(Long steamId, UUID userId, SteamProfile newProfile) {
        return steamProfileRepository.findByUserId(userId)
                .flatMap(existing -> {
                    existing.markAsExisting();
                    existing.setSteamCreated(newProfile.getSteamCreated());
                    existing.setProfileUrl(newProfile.getProfileUrl());
                    existing.setProfileImg(newProfile.getProfileImg());
                    existing.setUserId(userId);
                    return steamProfileRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> steamProfileRepository.save(newProfile)))
                .onErrorMap(e -> new GameRecommenderException(ErrorType.USER_STEAM_PROFILE_SAVE_ERROR, steamId));
    }

    private Mono<UserGameStats> upsertUserGameStats(UUID userId, UserGameStats newStats) {
        return userGameStatsRepository.findByUserId(userId)
                .flatMap(existing -> {
                    existing.markAsExisting();
                    existing.setSteamId(newStats.getSteamId());
                    existing.setUserId(userId);

                    existing.setTotalGamesOwned(newStats.getTotalGamesOwned());
                    existing.setTotalPlaytimeForever(newStats.getTotalPlaytimeForever());
                    existing.setTotalPlaytimeLastTwoWeeks(newStats.getTotalPlaytimeLastTwoWeeks());

                    existing.setMostPlayedGameId(newStats.getMostPlayedGameId());
                    existing.setMostPlayedGameName(newStats.getMostPlayedGameName());
                    existing.setMostPlayedGameHours(newStats.getMostPlayedGameHours());

                    existing.setLastPlayedGameId(newStats.getLastPlayedGameId());
                    existing.setLastPlayedGameName(newStats.getLastPlayedGameName());
                    existing.setLastPlaytime(newStats.getLastPlaytime());

                    // Favorite genre fields are not reliably derivable from Steam GetOwnedGames.
                    // They can be enriched later via Steam Store API if needed.
                    existing.setFavoriteGenre(newStats.getFavoriteGenre());
                    existing.setFavoriteGenreCount(newStats.getFavoriteGenreCount());
                    existing.setFavoriteGenreHours(newStats.getFavoriteGenreHours());

                    existing.setOwnedGamesSnapshot(newStats.getOwnedGamesSnapshot());

                    return userGameStatsRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> userGameStatsRepository.save(newStats)))
                .onErrorMap(e -> new GameRecommenderException(ErrorType.USER_GAME_STATS_SAVE_ERROR, newStats.getSteamId()));
    }

    private UserGameStats buildStats(Long steamId, UUID userId, SteamOwnedGamesResponse ownedGamesResponse) {
        SteamOwnedGamesResponse.Response response = ownedGamesResponse != null ? ownedGamesResponse.getResponse() : null;
        int gameCount = response != null ? response.getGameCount() : 0;
        List<SteamOwnedGamesResponse.Game> games = response != null && response.getGames() != null
                ? response.getGames()
                : Collections.emptyList();

        int totalPlaytimeForever = games.stream().mapToInt(SteamOwnedGamesResponse.Game::getPlaytimeForever).sum();
        int totalPlaytime2weeks = games.stream().mapToInt(SteamOwnedGamesResponse.Game::getPlaytime2weeks).sum();

        SteamOwnedGamesResponse.Game mostPlayed = games.stream()
                .max(Comparator.comparingInt(SteamOwnedGamesResponse.Game::getPlaytimeForever))
                .orElse(null);

        SteamOwnedGamesResponse.Game lastPlayed = games.stream()
                .filter(g -> g.getRtimeLastPlayed() != null && g.getRtimeLastPlayed() > 0)
                .max(Comparator.comparingInt(SteamOwnedGamesResponse.Game::getRtimeLastPlayed))
                .orElse(null);

        UserGameStats stats = new UserGameStats();
        stats.setSteamId(steamId);
        stats.setUserId(userId);

        stats.setTotalGamesOwned(gameCount);
        stats.setTotalPlaytimeForever(totalPlaytimeForever);
        stats.setTotalPlaytimeLastTwoWeeks(totalPlaytime2weeks);

        if (mostPlayed != null) {
            stats.setMostPlayedGameId(mostPlayed.getAppId());
            stats.setMostPlayedGameName(mostPlayed.getName());
            stats.setMostPlayedGameHours(mostPlayed.getPlaytimeForever() / 60);
        }

        if (lastPlayed != null) {
            stats.setLastPlayedGameId(lastPlayed.getAppId());
            stats.setLastPlayedGameName(lastPlayed.getName());
            stats.setLastPlaytime(lastPlayed.getRtimeLastPlayed());
        }

        stats.setOwnedGamesSnapshot(ownedGamesSnapshotMapper.toSnapshot(ownedGamesResponse));

        return stats;
    }

    private SteamPlayerResponse.Player firstPlayerOrNull(SteamPlayerResponse resp) {
        if (resp == null || resp.getResponse() == null || resp.getResponse().getPlayers() == null) {
            return null;
        }
        List<SteamPlayerResponse.Player> players = resp.getResponse().getPlayers();
        if (players.isEmpty()) {
            return null;
        }
        return players.getFirst();
    }
}

