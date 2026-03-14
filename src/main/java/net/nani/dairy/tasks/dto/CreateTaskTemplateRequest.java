package net.nani.dairy.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.TaskPriority;
import net.nani.dairy.tasks.TaskTemplateFrequency;
import net.nani.dairy.tasks.TaskType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskTemplateRequest {

    @NotBlank
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
    private Boolean active;
    private Integer reminderLeadMinutes;
    private Integer reminderRepeatMinutes;
    private Integer escalationDelayMinutes;
    private UserRole escalateToRole;
}
