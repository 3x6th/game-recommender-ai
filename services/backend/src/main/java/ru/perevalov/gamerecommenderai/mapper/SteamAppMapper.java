package ru.perevalov.gamerecommenderai.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.perevalov.gamerecommenderai.dto.steam.SteamAppResponseDto;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

@Mapper(componentModel = "spring")
public interface SteamAppMapper {
    @Mapping(target = "appid", source = "appid")
    @Mapping(target = "name", source = "name")
    SteamAppEntity toEntity(SteamAppResponseDto.AppList.App app);

}
