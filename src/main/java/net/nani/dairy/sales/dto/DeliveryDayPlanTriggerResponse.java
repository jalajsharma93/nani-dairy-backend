package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryDayPlanTriggerResponse {

    private LocalDate date;
    private int generatedTasks;
    private int eligibleCandidates;
    private int alreadyPlannedCandidates;
    private int blockedCandidates;
    private int autoAssignedTasks;
    private int optimizedTasks;
    private int optimizedRoutes;
    private long totalTasks;
    private long pendingTasks;
    private long unassignedPendingTasks;
    private String actor;
}
