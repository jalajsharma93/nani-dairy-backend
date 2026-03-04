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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "employee_comp_adjustment",
        indexes = {
                @Index(name = "idx_emp_comp_adj_month", columnList = "adjustment_month"),
                @Index(name = "idx_emp_comp_adj_employee", columnList = "employee_id"),
                @Index(name = "idx_emp_comp_adj_date", columnList = "adjustment_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeCompensationAdjustmentEntity {

    @Id
    @Column(name = "adjustment_id", nullable = false, length = 80)
    private String adjustmentId;

    @Column(name = "employee_id", nullable = false, length = 80)
    private String employeeId;

    @Column(name = "adjustment_month", nullable = false, length = 7)
    private String adjustmentMonth;

    @Column(name = "adjustment_date", nullable = false)
    private LocalDate adjustmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 40)
    private CompensationAdjustmentType adjustmentType;

    @Column(name = "amount", nullable = false)
    private double amount;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by_username", length = 120)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
