package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SaleComplianceOverrideAuditRepository extends JpaRepository<SaleComplianceOverrideAuditEntity, String> {
    List<SaleComplianceOverrideAuditEntity> findByDispatchDateBetweenOrderByCreatedAtDesc(LocalDate dateFrom, LocalDate dateTo);
}
