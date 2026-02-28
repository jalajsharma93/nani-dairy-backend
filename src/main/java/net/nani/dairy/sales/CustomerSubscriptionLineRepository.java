package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerSubscriptionLineRepository extends JpaRepository<CustomerSubscriptionLineEntity, String> {
    List<CustomerSubscriptionLineEntity> findByCustomerIdOrderByTaskShiftAscPreferredTimeAscCreatedAtAsc(String customerId);
    List<CustomerSubscriptionLineEntity> findByCustomerIdAndActiveTrueOrderByTaskShiftAscPreferredTimeAscCreatedAtAsc(String customerId);
    List<CustomerSubscriptionLineEntity> findByActiveTrue();
    Optional<CustomerSubscriptionLineEntity> findBySubscriptionLineIdAndCustomerId(String subscriptionLineId, String customerId);
}
