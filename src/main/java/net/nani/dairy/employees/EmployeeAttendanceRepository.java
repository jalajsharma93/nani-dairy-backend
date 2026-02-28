package net.nani.dairy.employees;

import net.nani.dairy.milk.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendanceEntity, String> {

    List<EmployeeAttendanceEntity> findByAttendanceDateOrderByEmployeeIdAsc(LocalDate attendanceDate);

    List<EmployeeAttendanceEntity> findByAttendanceDateAndShiftOrderByEmployeeIdAsc(LocalDate attendanceDate, Shift shift);

    List<EmployeeAttendanceEntity> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDescShiftAsc(
            String employeeId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    Optional<EmployeeAttendanceEntity> findByEmployeeIdAndAttendanceDateAndShift(
            String employeeId,
            LocalDate attendanceDate,
            Shift shift
    );
}
