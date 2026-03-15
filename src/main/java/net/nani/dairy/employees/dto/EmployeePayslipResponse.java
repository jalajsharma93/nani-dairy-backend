package net.nani.dairy.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.employees.EmployeeMonthlyPayoutStatus;
import net.nani.dairy.employees.EmployeeType;
import net.nani.dairy.sales.PaymentMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeePayslipResponse {
    private String month;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String employeeId;
    private String employeeName;
    private EmployeeType employeeType;
    private int presentDays;
    private int absentDays;
    private int presentShifts;
    private double totalHoursWorked;
    private double suggestedSalary;
    private double bonusAmount;
    private double productionIncentiveAmount;
    private double advanceAmount;
    private double deductionAmount;
    private double grossSalary;
    private double netPayableSalary;
    private EmployeeMonthlyPayoutStatus payoutStatus;
    private double paidAmount;
    private double pendingAmount;
    private PaymentMode paymentMode;
    private String paymentReferenceNo;
    private String payoutNotes;
    private OffsetDateTime approvedAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime generatedAt;
}
