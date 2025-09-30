package ru.perevalov.gamerecommenderai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.perevalov.gamerecommenderai.entity.SteamAppEntity;

import java.util.List;

@Repository
public interface SteamAppRepository extends JpaRepository<SteamAppEntity, Long>, SteamAppRepositoryCustom {
    @Query("SELECT e FROM SteamAppEntity e WHERE LOWER(e.name) IN :names")
    List<SteamAppEntity> findByLowerNameIn(@Param("names") List<String> names);
}
