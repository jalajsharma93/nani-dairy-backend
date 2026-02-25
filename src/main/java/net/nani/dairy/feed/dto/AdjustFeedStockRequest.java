package net.nani.dairy.feed.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustFeedStockRequest {

    @NotNull
    private Double quantityDelta;

    private String reason;
}
