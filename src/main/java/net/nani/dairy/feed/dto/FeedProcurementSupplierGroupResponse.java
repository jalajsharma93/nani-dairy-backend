package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProcurementSupplierGroupResponse {
    private String supplierName;
    private int itemsCount;
    private double totalRecommendedQty;
    private Double totalEstimatedCost;
    private List<FeedProcurementPlanItemResponse> items;
}
