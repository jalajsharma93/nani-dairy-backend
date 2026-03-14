package net.nani.dairy.tasks;

import jakarta.persistence.*;
import lombok.*;
import net.nani.dairy.auth.UserRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "task_template",
        indexes = {
                @Index(name = "idx_task_template_active", columnList = "active"),
                @Index(name = "idx_task_template_start_date", columnList = "start_date"),
                @Index(name = "idx_task_template_end_date", columnList = "end_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskTemplateEntity {

    @Id
    @Column(name = "task_template_id", length = 40, nullable = false, updatable = false)
    private String taskTemplateId;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "details", length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 24, nullable = false)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", length = 32, nullable = false)
    private UserRole assignedRole;

    @Column(name = "assigned_to_username", length = 120)
    private String assignedToUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private TaskPriority priority;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 16, nullable = false)
    private TaskTemplateFrequency frequency;

    @Column(name = "days_of_week_csv", length = 80)
    private String daysOfWeekCsv;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "reminder_lead_minutes")
    private Integer reminderLeadMinutes;

    @Column(name = "reminder_repeat_minutes")
    private Integer reminderRepeatMinutes;

    @Column(name = "escalation_delay_minutes")
    private Integer escalationDelayMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalate_to_role", length = 32)
    private UserRole escalateToRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
