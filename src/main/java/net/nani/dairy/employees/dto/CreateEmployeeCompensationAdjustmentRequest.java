package net.nani.dairy.employees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.employees.CompensationAdjustmentType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeCompensationAdjustmentRequest {

    @NotBlank
    @Size(max = 80)
    private String employeeId;

    @NotNull
    private LocalDate adjustmentDate;

    @NotNull
    private CompensationAdjustmentType adjustmentType;

    @NotNull
    @Positive
    private Double amount;

    @Size(max = 500)
    private String notes;
}
