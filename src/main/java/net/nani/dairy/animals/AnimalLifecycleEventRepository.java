package net.nani.dairy.animals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimalLifecycleEventRepository extends JpaRepository<AnimalLifecycleEventEntity, String> {
    List<AnimalLifecycleEventEntity> findByAnimalIdOrderByChangedAtDesc(String animalId);
}
