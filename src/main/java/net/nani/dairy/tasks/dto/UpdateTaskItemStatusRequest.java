package net.nani.dairy.tasks.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.nani.dairy.tasks.TaskStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskItemStatusRequest {
    @NotNull
    private TaskStatus status;
}
