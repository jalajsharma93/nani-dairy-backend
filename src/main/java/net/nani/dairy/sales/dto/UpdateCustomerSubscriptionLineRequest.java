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
import net.nani.dairy.sales.ProductType;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCustomerSubscriptionLineRequest {

    @NotNull
    private Shift taskShift;

    @NotNull
    private ProductType productType;

    @NotNull
    @Positive
    private Double quantity;

    @NotNull
    @Positive
    private Double unitPrice;

    private LocalTime preferredTime;

    @Size(max = 140)
    private String activeDaysCsv;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private Boolean active;

    @Size(max = 500)
    private String notes;
}
