package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSaleDeliveryRequest {

    @NotNull
    private Boolean delivered;

    @Size(max = 300)
    private String deliveryNote;

    @PositiveOrZero
    private Double collectedAmount;
}
