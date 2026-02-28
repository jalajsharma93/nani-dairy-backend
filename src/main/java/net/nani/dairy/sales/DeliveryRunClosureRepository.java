package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DeliveryRunClosureRepository extends JpaRepository<DeliveryRunClosureEntity, String> {

    List<DeliveryRunClosureEntity> findByDateOrderByClosedAtDesc(LocalDate date);
}
