package ru.perevalov.gamerecommenderai.mapper;

import org.mapstruct.Mapper;
import ru.perevalov.gamerecommenderai.dto.steam.SteamOwnedGamesResponse;
import ru.perevalov.gamerecommenderai.entity.embedded.OwnedGamesSnapshot;

@Mapper(componentModel = "spring")
public interface OwnedGamesSnapshotMapper {

    /**
     * Converts a {@link SteamOwnedGamesResponse} to an {@link OwnedGamesSnapshot}.
     */
    OwnedGamesSnapshot toSnapshot(SteamOwnedGamesResponse source);
}
