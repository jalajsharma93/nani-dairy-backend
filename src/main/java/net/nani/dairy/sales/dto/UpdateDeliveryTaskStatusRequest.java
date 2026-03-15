package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.DeliveryTaskStatus;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDeliveryTaskStatusRequest {

    private DeliveryTaskStatus status;

    @Positive
    private Double deliveredQtyLiters;

    @PositiveOrZero
    private Double collectedAmount;

    private Boolean overrideWithdrawalLock;

    @Size(max = 700)
    private String overrideReason;

    @Size(max = 500)
    private String notes;

    private OffsetDateTime expectedUpdatedAt;
}
