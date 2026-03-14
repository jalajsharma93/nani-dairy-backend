package net.nani.dairy.tasks;

import jakarta.persistence.*;
import lombok.*;
import net.nani.dairy.auth.UserRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "task_item",
        indexes = {
                @Index(name = "idx_task_item_date", columnList = "task_date"),
                @Index(name = "idx_task_item_type", columnList = "task_type"),
                @Index(name = "idx_task_item_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskItemEntity {

    @Id
    @Column(name = "task_id", length = 40, nullable = false, updatable = false)
    private String taskId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", length = 24, nullable = false)
    private TaskType taskType;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "details", length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", length = 32, nullable = false)
    private UserRole assignedRole;

    @Column(name = "assigned_to_username", length = 120)
    private String assignedToUsername;

    @Column(name = "assigned_by_username", length = 120)
    private String assignedByUsername;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TaskStatus status;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "source_ref_id", length = 100)
    private String sourceRefId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by", length = 120)
    private String completedBy;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalation_count", nullable = false, columnDefinition = "integer default 0")
    private Integer escalationCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (escalationCount == null) {
            escalationCount = 0;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
