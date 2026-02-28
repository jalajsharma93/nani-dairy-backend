package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthCloseSettlementBulkItemRequest {

    @NotNull
    private CustomerType customerType;

    @Size(max = 80)
    private String customerId;

    @NotBlank
    private String customerName;

    @PositiveOrZero
    private Double payoutAmount;

    private Boolean reconcileOpenCooperative;
}
