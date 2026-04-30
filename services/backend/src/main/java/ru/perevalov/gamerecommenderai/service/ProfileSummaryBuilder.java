package ru.perevalov.gamerecommenderai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.ProfileSummary;
import ru.perevalov.gamerecommenderai.dto.ProfileSummary.GameEntry;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot.Game;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileSummaryBuilder {

    private static final int MINUTES_IN_HOUR = 60;

    @Value("${app.recommender.prompt.top-by-playtime-list-size:10}")
    private int topByPlaytimeListSize;

    @Value("${app.recommender.prompt.all-games-list-size-limit:100}")
    private int allGamesListSizeLimit;

    private final ObjectMapper objectMapper;

    /**
     * Строит JSON-представление профиля пользователя на основе снимка библиотеки Steam.
     * <p>
     *
     * @param snapshot снимок библиотеки пользователя из Steam API
     * @return Optional содержащий JSON-строку с профилем {@link ProfileSummary}.
     * В случае ошибки при сериализации в JSON возвращает пустой Optional.
     */
    public Mono<String> buildJson(OwnedGamesSnapshot snapshot, Long steamId) {

        List<Game> games = getNotNullGameList(snapshot);

        ProfileSummary profileSummary = new ProfileSummary();

        profileSummary.setRecentlyPlayed(formRecentlyPlayedList(games));
        profileSummary.setTopByPlaytime(formTopByPlaytimeListExceptRecentPlayed(games));
        profileSummary.setAllGamesPlayed(formAllGamesPlayedList(games));
        profileSummary.setTotalGamesOwned(games.size());
        profileSummary.setTotalPlaytimeHours(countTotalPlaytimeHours(games));

        try {
            return Mono.just(objectMapper.writeValueAsString(profileSummary));
        } catch (JsonProcessingException e) {
            log.error("Error mapping profileSummary to JSON steamId={}", steamId, e);
            return Mono.empty();
        }
    }

    /**
     * Формирует список недавно сыгранных игр.
     * <p>
     * Включает все игры с {@code playtime2weeks > 0}.
     * Сортировка по убыванию {@code playtime2weeks}.
     */
    private List<GameEntry> formRecentlyPlayedList(List<Game> games) {
        return games
                .stream()
                .filter(this::gameWasPlayedRecently)
                .sorted(Comparator.comparing(Game::getPlaytime2weeks).reversed())
                .map(this::mapToGameEntry)
                .toList();
    }

    /**
     * Формирует топ игр по {@code playtimeForever}.
     * <p>
     * Исключает игры, уже попавшие в список недавно сыгранных ({@code playtime2weeks > 0}).
     * Сортировка по убыванию {@code playtimeForever}.
     */
    private List<GameEntry> formTopByPlaytimeListExceptRecentPlayed(List<Game> games) {
        return games
                .stream()
                .filter(game -> gameWasPlayed(game) && !gameWasPlayedRecently(game))
                .sorted(Comparator.comparing(Game::getPlaytimeForever).reversed())
                .limit(topByPlaytimeListSize)
                .map(this::mapToGameEntry)
                .toList();
    }

    /**
     * Формирует список всех когда-либо сыгранных игр ({@code playtimeForever > 0}).
     */
    private List<GameEntry> formAllGamesPlayedList(List<Game> games) {
        return games
                .stream()
                .filter(this::gameWasPlayed)
                .sorted(Comparator.comparing(Game::getPlaytimeForever).reversed())
                .limit(allGamesListSizeLimit)
                .map(this::mapToGameEntry)
                .toList();
    }

    /**
     * Подсчитывает суммарное время {@code playtimeForever} всех игр в библиотеке.
     */
    private Integer countTotalPlaytimeHours(List<Game> games) {
        int totalMinutes = games
                .stream()
                .map(Game::getPlaytimeForever)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return convertMinutesToHours(totalMinutes);
    }

    private GameEntry mapToGameEntry(Game game) {
        GameEntry entry = new GameEntry();
        entry.setName(game.getName());
        entry.setPlaytimeHours(convertMinutesToHours(game.getPlaytimeForever()));
        entry.setRecentPlaytimeHours(convertMinutesToHours(game.getPlaytime2weeks()));
        return entry;
    }

    private boolean gameWasPlayedRecently(Game game) {
        return game.getPlaytime2weeks() != null && game.getPlaytime2weeks() > 0;
    }

    private boolean gameWasPlayed(Game game) {
        return game.getPlaytimeForever() != null && game.getPlaytimeForever() > 0;
    }

    private List<Game> getNotNullGameList(OwnedGamesSnapshot snapshot) {
        if (snapshot.getResponse() == null || snapshot.getResponse().getGames() == null) {
            return List.of();
        }
        return snapshot.getResponse().getGames();
    }

    private int convertMinutesToHours(Integer minutes) {
        if (minutes == null) {
            return 0;
        }
        return (int) Math.ceil((double) minutes / MINUTES_IN_HOUR);
    }
}
