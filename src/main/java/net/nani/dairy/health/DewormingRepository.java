package net.nani.dairy.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DewormingRepository extends JpaRepository<DewormingEntity, String> {
    List<DewormingEntity> findByAnimalIdOrderByDoseDateDescCreatedAtDesc(String animalId);

    Optional<DewormingEntity> findByDewormingIdAndAnimalId(String dewormingId, String animalId);

    long countByNextDueDate(LocalDate date);

    long countByNextDueDateBefore(LocalDate date);

    long countByNextDueDateAfterAndNextDueDateLessThanEqual(LocalDate afterDate, LocalDate toDate);

    List<DewormingEntity> findByNextDueDateIsNotNullAndNextDueDateLessThanEqualOrderByNextDueDateAsc(LocalDate toDate);
}
