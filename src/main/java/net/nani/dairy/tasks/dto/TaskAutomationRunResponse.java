package net.nani.dairy.tasks.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAutomationRunResponse {
    private LocalDate date;
    private OffsetDateTime executedAt;
    private int processedTemplates;
    private int generatedTasks;
    private int updatedTasks;
    private int escalatedTasks;
    private int remindersTriggered;
    private List<TaskAutomationReminderResponse> reminders;
}
