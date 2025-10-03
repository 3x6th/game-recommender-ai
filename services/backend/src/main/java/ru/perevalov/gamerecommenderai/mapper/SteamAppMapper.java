package ru.perevalov.gamerecommenderai.mapper;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface SteamAppMapper {
    /**
     * Converts a {@link SteamAppResponseDto.AppList.App} to a {@link SteamAppEntity}.
     */
    SteamAppEntity toEntity(SteamAppResponseDto.AppList.App app);

    /**
     * Converts a {@link SteamAppEntity} to a {@link SteamAppResponseDto.AppList.App}.
     */
    SteamAppResponseDto.AppList.App toDto(SteamAppEntity entity);

    /**
     * Converts a list of {@link SteamAppResponseDto.AppList.App} to a list of {@link SteamAppEntity}.
     */
    List<SteamAppEntity> toEntities(List<SteamAppResponseDto.AppList.App> apps);

    /**
     * Converts a list of {@link SteamAppEntity} to a list of {@link SteamAppResponseDto.AppList.App}.
     */
    List<SteamAppResponseDto.AppList.App> toDtos(List<SteamAppEntity> entities);

    /**
     * Builds a {@link SteamAppResponseDto} from a list of {@link SteamAppEntity}. Returns empty response if list is null or empty.
     */
    default SteamAppResponseDto toResponseDto(List<SteamAppEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new SteamAppResponseDto(new SteamAppResponseDto.AppList(Collections.emptyList()));
        }
        List<SteamAppResponseDto.AppList.App> apps = toDtos(entities);
        return new SteamAppResponseDto(new SteamAppResponseDto.AppList(apps));
    }

    /**
     * Converts {@link SteamAppResponseDto} to a map of appid to app name. Returns empty map if input is null or invalid.
     */
    @IterableMapping(qualifiedByName = "toMapEntry")
    default Map<String, Long> toAppMap(SteamAppResponseDto dto) {
        if (dto == null || dto.appList() == null || dto.appList().apps() == null) {
            return Collections.emptyMap();
        }
        return dto.appList().apps().stream()
                .collect(Collectors.toMap(
                        SteamAppResponseDto.AppList.App::name,
                        SteamAppResponseDto.AppList.App::appid,
                        (existing, replacement) -> existing
                ));
    }
}
