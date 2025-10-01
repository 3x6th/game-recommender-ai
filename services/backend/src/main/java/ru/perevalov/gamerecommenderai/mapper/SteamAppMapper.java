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
    // ---- App <-> Entity ----
    SteamAppEntity toEntity(SteamAppResponseDto.AppList.App app);

    SteamAppResponseDto.AppList.App toDto(SteamAppEntity entity);

    List<SteamAppEntity> toEntities(List<SteamAppResponseDto.AppList.App> apps);

    List<SteamAppResponseDto.AppList.App> toDtos(List<SteamAppEntity> entities);

    // ---- Response DTO <-> Entity ----

    default SteamAppResponseDto toResponseDto(List<SteamAppEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new SteamAppResponseDto(new SteamAppResponseDto.AppList(Collections.emptyList()));
        }
        List<SteamAppResponseDto.AppList.App> apps = toDtos(entities); // MapStruct сгенерирует этот метод
        return new SteamAppResponseDto(new SteamAppResponseDto.AppList(apps));
    }

    // ---- DTO -> Map ----

    @IterableMapping(qualifiedByName = "toMapEntry")
    default Map<Long, String> toAppMap(SteamAppResponseDto dto) {
        if (dto == null || dto.appList() == null || dto.appList().apps() == null) {
            return Collections.emptyMap();
        }
        return dto.appList().apps().stream()
                .collect(Collectors.toMap(
                        SteamAppResponseDto.AppList.App::appid,
                        SteamAppResponseDto.AppList.App::name,
                        (existing, replacement) -> existing
                ));
    }
}
