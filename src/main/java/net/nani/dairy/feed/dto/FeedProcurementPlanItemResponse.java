package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.feed.FeedMaterialCategory;
import net.nani.dairy.feed.FeedMaterialUnit;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProcurementPlanItemResponse {
    private int rank;
    private String feedMaterialId;
    private String materialName;
    private FeedMaterialCategory category;
    private FeedMaterialUnit unit;
    private String supplierName;

    private double availableQty;
    private double reorderLevelQty;
    private Double daysOfStockLeft;
    private double recommendedOrderQty;
    private Double estimatedOrderCost;

    private String riskLevel;
    private String urgencyLevel;
    private int urgencyScore;
    private LocalDate suggestedOrderByDate;
    private String forecastBasis;
    private String recommendation;
}
