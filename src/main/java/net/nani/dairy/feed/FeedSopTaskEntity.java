package net.nani.dairy.feed;

import jakarta.persistence.*;
import lombok.*;
import net.nani.dairy.auth.UserRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "feed_sop_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedSopTaskEntity {

    @Id
    @Column(name = "feed_task_id", length = 32, nullable = false, updatable = false)
    private String feedTaskId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "details", length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", length = 32, nullable = false)
    private UserRole assignedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private FeedSopTaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private FeedSopTaskStatus status;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by", length = 120)
    private String completedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
