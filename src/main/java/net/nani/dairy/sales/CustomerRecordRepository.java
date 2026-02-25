package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRecordRepository extends JpaRepository<CustomerRecordEntity, String> {
    List<CustomerRecordEntity> findByIsActive(boolean isActive);
    List<CustomerRecordEntity> findByCustomerNameIgnoreCase(String customerName);
}
