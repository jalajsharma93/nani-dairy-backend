package net.nani.dairy.milk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MilkBatchRepository extends JpaRepository<MilkBatchEntity, String> {
    Optional<MilkBatchEntity> findByDateAndShift(LocalDate date, Shift shift);
}