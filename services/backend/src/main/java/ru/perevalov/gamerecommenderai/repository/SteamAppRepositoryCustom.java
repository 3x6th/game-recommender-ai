package ru.perevalov.gamerecommenderai.repository;

import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.util.List;

public interface SteamAppRepositoryCustom {
    void batchInsert(List<SteamAppEntity> entities);

}
