package net.nani.dairy.employees;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.PaymentMode;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "employee_monthly_payout",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_monthly_payout_month_employee",
                        columnNames = {"employee_id", "payout_month"}
                )
        },
        indexes = {
                @Index(name = "idx_emp_monthly_payout_month", columnList = "payout_month"),
                @Index(name = "idx_emp_monthly_payout_employee", columnList = "employee_id"),
                @Index(name = "idx_emp_monthly_payout_status", columnList = "payout_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeMonthlyPayoutEntity {

    @Id
    @Column(name = "payout_id", nullable = false, length = 90)
    private String payoutId;

    @Column(name = "employee_id", nullable = false, length = 80)
    private String employeeId;

    @Column(name = "payout_month", nullable = false, length = 7)
    private String payoutMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status", nullable = false, length = 30)
    private EmployeeMonthlyPayoutStatus payoutStatus;

    @Column(name = "net_payable_salary", nullable = false)
    private double netPayableSalary;

    @Column(name = "paid_amount", nullable = false)
    private double paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 30)
    private PaymentMode paymentMode;

    @Column(name = "payment_reference_no", length = 120)
    private String paymentReferenceNo;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "approved_by_username", length = 120)
    private String approvedByUsername;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "paid_by_username", length = 120)
    private String paidByUsername;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (payoutStatus == null) {
            payoutStatus = EmployeeMonthlyPayoutStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
        if (payoutStatus == null) {
            payoutStatus = EmployeeMonthlyPayoutStatus.PENDING;
        }
    }
}
