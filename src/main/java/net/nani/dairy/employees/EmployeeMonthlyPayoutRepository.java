package net.nani.dairy.employees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeMonthlyPayoutRepository extends JpaRepository<EmployeeMonthlyPayoutEntity, String> {

    Optional<EmployeeMonthlyPayoutEntity> findByEmployeeIdAndPayoutMonth(String employeeId, String payoutMonth);

    List<EmployeeMonthlyPayoutEntity> findByPayoutMonthOrderByEmployeeIdAsc(String payoutMonth);

    List<EmployeeMonthlyPayoutEntity> findByPayoutMonthAndEmployeeIdIn(String payoutMonth, Collection<String> employeeIds);
}
