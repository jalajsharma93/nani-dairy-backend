package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.health.WorklistDueStatus;
import net.nani.dairy.health.WorklistPriority;
import net.nani.dairy.health.WorklistTaskType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorklistItemResponse {
    private String taskId;
    private WorklistTaskType type;
    private WorklistPriority priority;
    private WorklistDueStatus dueStatus;
    private LocalDate dueDate;
    private String animalId;
    private String animalTag;
    private String sourceId;
    private String title;
    private String description;
}
