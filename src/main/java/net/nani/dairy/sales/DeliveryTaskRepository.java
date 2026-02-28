package net.nani.dairy.sales;

import net.nani.dairy.milk.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTaskEntity, String> {
    List<DeliveryTaskEntity> findByTaskDate(LocalDate taskDate);
    List<DeliveryTaskEntity> findByTaskDateAndStatus(LocalDate taskDate, DeliveryTaskStatus status);
    Optional<DeliveryTaskEntity> findBySourceRefId(String sourceRefId);
    Optional<DeliveryTaskEntity> findByTaskDateAndCustomerIdAndTaskShift(LocalDate taskDate, String customerId, Shift taskShift);
    Optional<DeliveryTaskEntity> findByTaskDateAndCustomerIdAndTaskShiftAndProductTypeAndPreferredTimeAndStatus(
            LocalDate taskDate,
            String customerId,
            Shift taskShift,
            ProductType productType,
            LocalTime preferredTime,
            DeliveryTaskStatus status
    );
}
