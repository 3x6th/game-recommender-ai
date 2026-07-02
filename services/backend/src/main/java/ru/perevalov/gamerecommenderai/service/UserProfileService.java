package ru.perevalov.gamerecommenderai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.perevalov.gamerecommenderai.dto.CurrentUserProfileResponse;
import ru.perevalov.gamerecommenderai.entity.SteamProfile;
import ru.perevalov.gamerecommenderai.exception.ErrorType;
import ru.perevalov.gamerecommenderai.exception.GameRecommenderException;
import ru.perevalov.gamerecommenderai.repository.SteamProfileRepository;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final RequestContextResolver requestContextResolver;
    private final SteamProfileRepository steamProfileRepository;

    /**
     * Returns profile data for the user identified by the verified request identity.
     * Steam ID is deliberately not accepted as an argument so one user cannot request
     * another user's profile through this endpoint.
     */
    public Mono<CurrentUserProfileResponse> getCurrentUserProfile() {
        return requestContextResolver.resolve()
                .flatMap(context -> {
                    if (!context.isUser()) {
                        return Mono.error(new GameRecommenderException(ErrorType.AUTHENTICATION_REQUIRED));
                    }

                    return steamProfileRepository.findByUserId(context.userId())
                            .map(profile -> toResponse(context.steamId(), profile))
                            .defaultIfEmpty(toResponse(context.steamId(), null));
                });
    }

    private CurrentUserProfileResponse toResponse(Long steamId, SteamProfile profile) {
        return new CurrentUserProfileResponse(
                steamId.toString(),
                profile != null ? profile.getProfileImg() : null,
                profile != null ? profile.getProfileUrl() : null
        );
    }
}
