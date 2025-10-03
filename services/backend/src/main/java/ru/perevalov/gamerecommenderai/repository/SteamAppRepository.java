package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

@Repository
public interface SteamAppRepository extends JpaRepository<SteamAppEntity, Long>, SteamAppRepositoryCustom {
}
