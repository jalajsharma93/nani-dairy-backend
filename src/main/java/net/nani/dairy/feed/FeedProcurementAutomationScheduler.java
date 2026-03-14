package net.nani.dairy.feed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedProcurementAutomationScheduler {

    private final FeedManagementService feedManagementService;

    @Value("${app.feed.procurement-automation-enabled:true}")
    private boolean automationEnabled;

    @Value("${app.feed.procurement-lookback-days:30}")
    private int lookbackDays;

    @Value("${app.feed.procurement-horizon-days:30}")
    private int horizonDays;

    @Scheduled(cron = "${app.feed.procurement-automation-cron:0 10 5 * * *}")
    public void scheduledProcurementTaskGeneration() {
        if (!automationEnabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        try {
            var run = feedManagementService.generateProcurementTasksAutomated(
                    today,
                    lookbackDays,
                    horizonDays,
                    today,
                    "system:feed-procurement:auto",
                    "Scheduled procurement automation run"
            );
            log.info(
                    "Feed procurement automation run {}: considered={}, created={}, skipped={}",
                    run.getFeedProcurementRunId(),
                    run.getConsideredItems(),
                    run.getCreatedTasks(),
                    run.getSkippedTasks()
            );
        } catch (Exception e) {
            log.error("Feed procurement automation failed", e);
        }
    }
}
