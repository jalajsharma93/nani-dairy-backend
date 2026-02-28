package net.nani.dairy.employees.dto;

import lombok.*;
import net.nani.dairy.employees.AttendanceStatus;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAttendanceResponse {
    private String attendanceId;
    private String employeeId;
    private String employeeName;
    private LocalDate attendanceDate;
    private Shift shift;
    private AttendanceStatus status;
    private Double hoursWorked;
    private String notes;
    private String markedByUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
