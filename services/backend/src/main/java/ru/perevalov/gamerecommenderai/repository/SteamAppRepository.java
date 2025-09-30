package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

public interface SteamAppRepository extends JpaRepository<SteamAppEntity, Long> {
}
