package net.nani.dairy.employees;

import jakarta.persistence.*;
import lombok.*;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "employee_attendance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_attendance_date_shift",
                        columnNames = {"employee_id", "attendance_date", "shift"}
                )
        },
        indexes = {
                @Index(name = "idx_emp_attendance_date", columnList = "attendance_date"),
                @Index(name = "idx_emp_attendance_employee", columnList = "employee_id"),
                @Index(name = "idx_emp_attendance_shift", columnList = "shift")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAttendanceEntity {

    @Id
    @Column(name = "attendance_id", nullable = false, length = 100, updatable = false)
    private String attendanceId;

    @Column(name = "employee_id", nullable = false, length = 80)
    private String employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 10)
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttendanceStatus status;

    @Column(name = "hours_worked")
    private Double hoursWorked;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "marked_by_username", length = 120)
    private String markedByUsername;

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
