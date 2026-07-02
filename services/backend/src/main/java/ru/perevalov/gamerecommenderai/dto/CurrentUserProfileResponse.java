package ru.perevalov.gamerecommenderai.dto;

/**
 * Public Steam profile data for the currently authenticated user.
 *
 * @param steamId current user's Steam ID
 * @param avatarUrl full-size Steam avatar URL, if the profile has been synced
 * @param profileUrl public Steam Community profile URL, if available
 */
public record CurrentUserProfileResponse(
        String steamId,
        String avatarUrl,
        String profileUrl
) {
}
