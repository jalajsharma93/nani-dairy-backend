package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.delivery.day-planner-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeliveryDayPlannerScheduler {

    private final DeliveryTaskService deliveryTaskService;

    @Scheduled(cron = "${app.delivery.day-planner-cron:0 */30 * * * *}")
    public void triggerDayPlanner() {
        LocalDate today = LocalDate.now();
        try {
            deliveryTaskService.triggerDayPlan(today, "system-day-planner", true);
            deliveryTaskService.triggerDayPlan(today.plusDays(1), "system-day-planner", false);
        } catch (Exception ex) {
            log.error("Delivery day planner scheduler failed", ex);
        }
    }
}
