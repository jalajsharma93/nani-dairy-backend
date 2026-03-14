package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.feed.FeedMaterialCategory;
import net.nani.dairy.feed.FeedMaterialUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedInventoryForecastItemResponse {
    private String feedMaterialId;
    private String materialName;
    private FeedMaterialCategory category;
    private FeedMaterialUnit unit;
    private double availableQty;
    private double reorderLevelQty;
    private Double costPerUnit;
    private boolean lowStock;

    private double estimatedDailyConsumptionQty;
    private Double daysOfStockLeft;
    private double requiredQty30Days;
    private double requiredQty90Days;
    private double recommendedReorderQty30Days;
    private double recommendedReorderQty90Days;
    private double projectedStockAfter30Days;
    private double projectedStockAfter90Days;

    private String riskLevel; // LOW | MEDIUM | HIGH
    private String recommendation;
    private String forecastBasis; // LOG_BASED | REORDER_LEVEL_ONLY
}
