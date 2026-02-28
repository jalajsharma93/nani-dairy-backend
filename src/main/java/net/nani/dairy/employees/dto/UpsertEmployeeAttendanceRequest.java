package net.nani.dairy.employees.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.employees.AttendanceStatus;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertEmployeeAttendanceRequest {

    @NotBlank
    private String employeeId;

    @NotNull
    private LocalDate attendanceDate;

    @NotNull
    private Shift shift;

    @NotNull
    private AttendanceStatus status;

    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "24.0", inclusive = true)
    private Double hoursWorked;

    private String notes;
}
