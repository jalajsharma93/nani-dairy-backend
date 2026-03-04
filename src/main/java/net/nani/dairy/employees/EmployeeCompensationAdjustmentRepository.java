package net.nani.dairy.employees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeCompensationAdjustmentRepository extends JpaRepository<EmployeeCompensationAdjustmentEntity, String> {

    List<EmployeeCompensationAdjustmentEntity> findByAdjustmentMonthOrderByAdjustmentDateAscEmployeeIdAsc(String adjustmentMonth);

    List<EmployeeCompensationAdjustmentEntity> findByAdjustmentMonthAndEmployeeIdOrderByAdjustmentDateAsc(
            String adjustmentMonth,
            String employeeId
    );
}
