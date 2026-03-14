package net.nani.dairy.milk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MilkEntryRepository extends JpaRepository<MilkEntryEntity, String> {
    List<MilkEntryEntity> findByDateAndShift(LocalDate date, Shift shift);
    List<MilkEntryEntity> findByDateBetween(LocalDate dateFrom, LocalDate dateTo);

    Optional<MilkEntryEntity> findByDateAndShiftAndAnimalId(LocalDate date, Shift shift, String animalId);

    List<MilkEntryEntity> findByAnimalIdAndDateBetweenOrderByDateDescCreatedAtDesc(
            String animalId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    List<MilkEntryEntity> findByDateBetweenAndQcStatusIn(
            LocalDate dateFrom,
            LocalDate dateTo,
            Collection<QcStatus> qcStatuses
    );

    List<MilkEntryEntity> findByAnimalIdInAndDateBetween(
            List<String> animalIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );
}
