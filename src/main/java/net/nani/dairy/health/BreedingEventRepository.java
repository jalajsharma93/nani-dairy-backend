package net.nani.dairy.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BreedingEventRepository extends JpaRepository<BreedingEventEntity, String> {
    List<BreedingEventEntity> findByAnimalIdOrderByHeatDateDescCreatedAtDesc(String animalId);

    Optional<BreedingEventEntity> findByBreedingEventIdAndAnimalId(String breedingEventId, String animalId);

    long countByExpectedCalvingDateAndActualCalvingDateIsNull(LocalDate date);

    long countByExpectedCalvingDateAfterAndExpectedCalvingDateLessThanEqualAndActualCalvingDateIsNull(
            LocalDate fromDate,
            LocalDate toDate
    );

    long countByExpectedCalvingDateBeforeAndActualCalvingDateIsNull(LocalDate date);

    long countByPregnancyResultAndActualCalvingDateIsNull(BreedingPregnancyResult pregnancyResult);

    List<BreedingEventEntity> findByExpectedCalvingDateIsNotNullAndExpectedCalvingDateLessThanEqualAndActualCalvingDateIsNullOrderByExpectedCalvingDateAsc(
            LocalDate toDate
    );

    List<BreedingEventEntity> findByInseminationDateIsNotNullAndPregnancyCheckDateIsNullAndActualCalvingDateIsNullOrderByInseminationDateAsc();

    List<BreedingEventEntity> findByPregnancyResultAndInseminationDateIsNotNullAndActualCalvingDateIsNullAndInseminationDateAfter(
            BreedingPregnancyResult pregnancyResult,
            LocalDate fromDate
    );
}
