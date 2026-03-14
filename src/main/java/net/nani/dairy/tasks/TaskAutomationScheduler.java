package net.nani.dairy.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nani.dairy.tasks.dto.TaskAutomationRunResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.tasks.automation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskAutomationScheduler {

    private final TaskTemplateService taskTemplateService;

    @Scheduled(cron = "${app.tasks.automation-cron:0 */20 * * * *}")
    public void runAutomationCycle() {
        try {
            TaskAutomationRunResponse result = taskTemplateService.run(LocalDate.now(), false, "system-task-automation");
            if (result.getGeneratedTasks() > 0 || result.getEscalatedTasks() > 0 || result.getRemindersTriggered() > 0) {
                log.info(
                        "Task automation run completed: date={}, templates={}, generated={}, escalated={}, reminders={}",
                        result.getDate(),
                        result.getProcessedTemplates(),
                        result.getGeneratedTasks(),
                        result.getEscalatedTasks(),
                        result.getRemindersTriggered()
                );
            }
        } catch (Exception ex) {
            log.error("Task automation scheduler failed", ex);
        }
    }
}
