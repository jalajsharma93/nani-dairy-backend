package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProcurementPlanResponse {
    private LocalDate date;
    private int lookbackDays;
    private int horizonDays;

    private int totalMaterialsConsidered;
    private int itemsPlanned;
    private int highUrgencyItems;
    private int mediumUrgencyItems;
    private int lowUrgencyItems;

    private double totalRecommendedQty;
    private Double totalEstimatedCost;

    private List<FeedProcurementSupplierGroupResponse> supplierGroups;
    private List<FeedProcurementPlanItemResponse> items;
}

