package net.nani.dairy.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.employees.EmployeeMonthlyPayoutStatus;
import net.nani.dairy.sales.PaymentMode;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeMonthlyPayoutResponse {
    private String payoutId;
    private String month;
    private String employeeId;
    private String employeeName;
    private EmployeeMonthlyPayoutStatus payoutStatus;
    private double netPayableSalary;
    private double paidAmount;
    private double pendingAmount;
    private PaymentMode paymentMode;
    private String paymentReferenceNo;
    private String notes;
    private String approvedByUsername;
    private OffsetDateTime approvedAt;
    private String paidByUsername;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
