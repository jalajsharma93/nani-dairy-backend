package net.nani.dairy.tasks.dto;

import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.TaskPriority;
import net.nani.dairy.tasks.TaskTemplateFrequency;
import net.nani.dairy.tasks.TaskType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskTemplateResponse {
    private String taskTemplateId;
    private String title;
    private String details;
    private TaskType taskType;
    private UserRole assignedRole;
    private String assignedToUsername;
    private TaskPriority priority;
    private LocalTime dueTime;
    private TaskTemplateFrequency frequency;
    private List<String> daysOfWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
    private Integer reminderLeadMinutes;
    private Integer reminderRepeatMinutes;
    private Integer escalationDelayMinutes;
    private UserRole escalateToRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
