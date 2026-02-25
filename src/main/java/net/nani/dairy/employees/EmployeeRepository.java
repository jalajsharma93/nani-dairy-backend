package net.nani.dairy.employees;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, String> {
    List<EmployeeEntity> findByIsActive(boolean isActive);

    List<EmployeeEntity> findByType(EmployeeType type);

    List<EmployeeEntity> findByIsActiveAndType(boolean isActive, EmployeeType type);

    boolean existsByNameAndType(String name, EmployeeType type);
}
