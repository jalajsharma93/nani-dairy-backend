package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionInvoiceStatusAuditRepository extends JpaRepository<SubscriptionInvoiceStatusAuditEntity, String> {
    List<SubscriptionInvoiceStatusAuditEntity> findByInvoiceMonthOrderByCreatedAtDesc(String invoiceMonth);

    List<SubscriptionInvoiceStatusAuditEntity> findByInvoiceMonthAndCustomerIdOrderByCreatedAtDesc(
            String invoiceMonth,
            String customerId
    );
}

