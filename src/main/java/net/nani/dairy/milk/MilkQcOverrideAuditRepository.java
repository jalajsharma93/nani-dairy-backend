package net.nani.dairy.milk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MilkQcOverrideAuditRepository extends JpaRepository<MilkQcOverrideAuditEntity, String> {
    List<MilkQcOverrideAuditEntity> findByBatchDateBetweenOrderByCreatedAtDesc(LocalDate dateFrom, LocalDate dateTo);
}
