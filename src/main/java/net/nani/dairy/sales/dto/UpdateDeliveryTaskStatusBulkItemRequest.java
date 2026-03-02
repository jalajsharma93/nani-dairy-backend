package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.DeliveryTaskStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDeliveryTaskStatusBulkItemRequest {

    @NotBlank
    private String deliveryTaskId;

    @NotNull
    private DeliveryTaskStatus status;

    @Positive
    private Double deliveredQtyLiters;

    @PositiveOrZero
    private Double collectedAmount;

    @Size(max = 500)
    private String notes;
}
