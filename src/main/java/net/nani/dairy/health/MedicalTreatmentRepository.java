package net.nani.dairy.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MedicalTreatmentRepository extends JpaRepository<MedicalTreatmentEntity, String> {
    List<MedicalTreatmentEntity> findByAnimalIdOrderByTreatmentDateDescCreatedAtDesc(String animalId);

    Optional<MedicalTreatmentEntity> findByTreatmentIdAndAnimalId(String treatmentId, String animalId);

    List<MedicalTreatmentEntity> findByAnimalIdInAndWithdrawalTillDateIsNotNullAndTreatmentDateLessThanEqualAndWithdrawalTillDateGreaterThanEqual(
            List<String> animalIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );
}
