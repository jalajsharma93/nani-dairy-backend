package net.nani.dairy.feed.dto;

import lombok.*;
import net.nani.dairy.feed.FeedMaterialCategory;
import net.nani.dairy.feed.FeedMaterialUnit;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedMaterialResponse {
    private String feedMaterialId;
    private String materialName;
    private FeedMaterialCategory category;
    private FeedMaterialUnit unit;
    private double availableQty;
    private double reorderLevelQty;
    private Double costPerUnit;
    private String supplierName;
    private String notes;
    private boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
