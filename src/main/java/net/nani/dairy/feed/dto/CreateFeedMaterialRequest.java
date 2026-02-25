package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import net.nani.dairy.feed.FeedMaterialCategory;
import net.nani.dairy.feed.FeedMaterialUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFeedMaterialRequest {

    @NotBlank
    private String materialName;

    @NotNull
    private FeedMaterialCategory category;

    @NotNull
    private FeedMaterialUnit unit;

    @NotNull
    @PositiveOrZero
    private Double availableQty;

    @NotNull
    @PositiveOrZero
    private Double reorderLevelQty;

    @PositiveOrZero
    private Double costPerUnit;

    private String supplierName;
    private String notes;
}
