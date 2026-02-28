package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.PaymentMode;
import net.nani.dairy.sales.ProductType;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryTaskRequest {

    @NotNull
    private LocalDate taskDate;

    @Size(max = 80)
    private String customerId;

    @Size(max = 120)
    private String customerName;

    @Size(max = 100)
    private String assignedToUsername;

    @Positive
    @NotNull
    private Double plannedQtyLiters;

    @Positive
    private Double unitPrice;

    private ProductType productType;

    private Shift taskShift;

    private LocalTime preferredTime;

    private PaymentMode paymentMode;

    @Size(max = 500)
    private String notes;
}
