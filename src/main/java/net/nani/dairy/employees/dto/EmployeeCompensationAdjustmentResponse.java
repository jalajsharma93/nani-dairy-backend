package net.nani.dairy.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.employees.CompensationAdjustmentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeCompensationAdjustmentResponse {
    private String adjustmentId;
    private String employeeId;
    private String employeeName;
    private String adjustmentMonth;
    private LocalDate adjustmentDate;
    private CompensationAdjustmentType adjustmentType;
    private double amount;
    private String notes;
    private String createdByUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
