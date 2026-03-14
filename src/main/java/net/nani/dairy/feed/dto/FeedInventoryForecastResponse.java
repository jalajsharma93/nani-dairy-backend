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
public class FeedInventoryForecastResponse {
    private LocalDate date;
    private int lookbackDays;
    private int feedLogsCount;
    private double estimatedDailyConsumptionTotalKg;
    private int highRiskMaterials;
    private int mediumRiskMaterials;
    private int lowRiskMaterials;
    private double totalRecommendedReorderQty30Days;
    private double totalRecommendedReorderQty90Days;
    private double totalRecommendedReorderCost30Days;
    private double totalRecommendedReorderCost90Days;
    private List<FeedInventoryForecastItemResponse> items;
}
