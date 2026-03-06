package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerSubscriptionInvoiceRepository extends JpaRepository<CustomerSubscriptionInvoiceEntity, String> {

    Optional<CustomerSubscriptionInvoiceEntity> findByCustomerIdAndInvoiceMonth(String customerId, String invoiceMonth);

    List<CustomerSubscriptionInvoiceEntity> findByInvoiceMonthOrderByCustomerNameAsc(String invoiceMonth);

    List<CustomerSubscriptionInvoiceEntity> findByInvoiceMonthAndCustomerTypeOrderByCustomerNameAsc(
            String invoiceMonth,
            CustomerType customerType
    );
}
