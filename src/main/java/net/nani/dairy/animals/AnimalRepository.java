package net.nani.dairy.animals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnimalRepository extends JpaRepository<AnimalEntity, String> {
    List<AnimalEntity> findByIsActive(boolean isActive);

    List<AnimalEntity> findByStatus(AnimalStatus status);

    List<AnimalEntity> findByIsActiveAndStatus(boolean isActive, AnimalStatus status);

    List<AnimalEntity> findByAnimalIdStartingWith(String prefix);

    boolean existsByTag(String tag);

    Optional<AnimalEntity> findByTag(String tag);
}
