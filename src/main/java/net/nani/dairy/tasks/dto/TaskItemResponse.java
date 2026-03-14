package net.nani.dairy.tasks.dto;

import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.TaskPriority;
import net.nani.dairy.tasks.TaskStatus;
import net.nani.dairy.tasks.TaskType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskItemResponse {
    private String taskId;
    private LocalDate taskDate;
    private TaskType taskType;
    private String title;
    private String details;
    private UserRole assignedRole;
    private String assignedToUsername;
    private String assignedByUsername;
    private LocalDateTime assignedAt;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalTime dueTime;
    private String sourceRefId;
    private LocalDateTime completedAt;
    private String completedBy;
    private LocalDateTime reminderSentAt;
    private LocalDateTime escalatedAt;
    private Integer escalationCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
