package ru.perevalov.gamerecommenderai.service;

import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.entity.UserGameStats;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;

@Component
public class UserGameStatsValidator {

    private static final String TOTAL_GAMES_OWNED = "totalGamesOwned";
    private static final String TOTAL_PLAYTIME_FOREVER = "totalPlaytimeForever";
    private static final String TOTAL_PLAYTIME_LAST_TWO_WEEKS = "totalPlaytimeLastTwoWeeks";
    private static final String MOST_PLAYED_GAME_HOURS = "mostPlayedGameHours";
    private static final String MOST_PLAYED_GAME_ID = "mostPlayedGameId";
    private static final String MOST_PLAYED_GAME_NAME = "mostPlayedGameName";
    private static final String LAST_PLAYTIME = "lastPlaytime";
    private static final String LAST_PLAYED_GAME_ID = "lastPlayedGameId";
    private static final String LAST_PLAYED_GAME_NAME = "lastPlayedGameName";

    public void validate(UserGameStats stats) {
        Long id = stats.getSteamId();
        validateFieldIsNonNegative(stats.getTotalGamesOwned(), TOTAL_GAMES_OWNED, id);
        validateFieldIsNonNegative(stats.getTotalPlaytimeForever(), TOTAL_PLAYTIME_FOREVER, id);
        validateFieldIsNonNegative(stats.getTotalPlaytimeLastTwoWeeks(), TOTAL_PLAYTIME_LAST_TWO_WEEKS, id);
        validateFieldIsNonNegative(stats.getMostPlayedGameHours(), MOST_PLAYED_GAME_HOURS, id);
        validateFieldIsNonNegative(stats.getLastPlaytime(), LAST_PLAYTIME, id);

        validateConsistency(
                stats.getMostPlayedGameId(), stats.getMostPlayedGameName(),
                MOST_PLAYED_GAME_ID, MOST_PLAYED_GAME_NAME, id);
        validateConsistency(
                stats.getMostPlayedGameId(), stats.getMostPlayedGameHours(),
                MOST_PLAYED_GAME_ID, MOST_PLAYED_GAME_HOURS, id);
        validateConsistency(
                stats.getLastPlayedGameId(), stats.getLastPlayedGameName(),
                LAST_PLAYED_GAME_ID, LAST_PLAYED_GAME_NAME, id);
    }

    private void validateFieldIsNonNegative(Number fieldValue, String fieldName, Long steamId) {
        if (fieldValue != null && fieldValue.longValue() < 0) {
            throw new GameRecommenderException(
                    ErrorType.USER_GAME_STATS_VALIDATION_ERROR,
                    fieldName + " is negative",
                    steamId
            );
        }
    }

    private void validateConsistency(Object requiringField, Object requiredField,
                                     String requiringName, String requiredName, Long steamId) {
        if (requiringField != null && requiredField == null) {
            throw new GameRecommenderException(
                    ErrorType.USER_GAME_STATS_VALIDATION_ERROR,
                    requiredName + " is null when " + requiringName + " is set",
                    steamId
            );
        }
    }
}
