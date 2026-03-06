package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRouteOptimizationResponse {
    private LocalDate date;
    private Shift shift;
    private String routeName;
    private int optimizedTasks;
    private int optimizedRoutes;
    private long pendingTasksInScope;
    private long deliveredTasksInScope;
    private String actor;
    private OffsetDateTime optimizedAt;
}
