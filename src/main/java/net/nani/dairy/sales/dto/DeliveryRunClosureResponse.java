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
public class DeliveryRunClosureResponse {

    private String runClosureId;
    private LocalDate date;
    private String routeName;
    private Shift shift;
    private long totalStops;
    private long deliveredStops;
    private long pendingStops;
    private long skippedStops;
    private double expectedCollection;
    private double actualCollection;
    private double variance;
    private double cashCollection;
    private double upiCollection;
    private double otherCollection;
    private String notes;
    private String closedBy;
    private OffsetDateTime closedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
