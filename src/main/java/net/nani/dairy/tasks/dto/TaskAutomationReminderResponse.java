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
public class TaskAutomationReminderResponse {
    private String taskId;
    private LocalDate taskDate;
    private String title;
    private TaskType taskType;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalTime dueTime;
    private UserRole assignedRole;
    private String assignedToUsername;
    private String message;
    private LocalDateTime reminderAt;
}
