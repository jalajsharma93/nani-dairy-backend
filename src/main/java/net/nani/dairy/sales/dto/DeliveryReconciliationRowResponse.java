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
public class DeliveryReconciliationRowResponse {
    private LocalDate date;
    private String deliveryUsername;
    private long assignedTasks;
    private long deliveredTasks;
    private long skippedTasks;
    private long pendingTasks;
    private double plannedQty;
    private double deliveredQty;
    private double collectedAmount;
    private double pendingAmount;
}
