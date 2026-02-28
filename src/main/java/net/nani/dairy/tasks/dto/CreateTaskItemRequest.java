package net.nani.dairy.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.TaskPriority;
import net.nani.dairy.tasks.TaskType;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskItemRequest {
    @NotNull
    private LocalDate taskDate;

    private TaskType taskType;

    @NotBlank
    private String title;

    private String details;
    private UserRole assignedRole;
    private String assignedToUsername;
    private TaskPriority priority;
    private LocalTime dueTime;
    private String sourceRefId;
}
