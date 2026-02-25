package net.nani.dairy.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VaccinationRepository extends JpaRepository<VaccinationEntity, String> {
    List<VaccinationEntity> findByAnimalIdOrderByDoseDateDescCreatedAtDesc(String animalId);

    Optional<VaccinationEntity> findByVaccinationIdAndAnimalId(String vaccinationId, String animalId);

    long countByNextDueDate(LocalDate date);

    long countByNextDueDateBefore(LocalDate date);

    long countByNextDueDateAfterAndNextDueDateLessThanEqual(LocalDate afterDate, LocalDate toDate);

    List<VaccinationEntity> findByNextDueDateIsNotNullAndNextDueDateLessThanEqualOrderByNextDueDateAsc(LocalDate toDate);
}
