package net.nani.dairy.employees.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.employees.EmployeeMonthlyPayoutStatus;
import net.nani.dairy.sales.PaymentMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertEmployeeMonthlyPayoutRequest {

    @NotBlank(message = "month is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be YYYY-MM")
    private String month;

    @NotBlank(message = "employeeId is required")
    private String employeeId;

    private EmployeeMonthlyPayoutStatus payoutStatus;

    private Double netPayableSalary;

    private Double paidAmount;

    private PaymentMode paymentMode;

    private String paymentReferenceNo;

    private String notes;
}
