package net.nani.dairy.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.PaymentMode;
import net.nani.dairy.sales.ProductType;
import net.nani.dairy.sales.SettlementCycle;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSaleRequest {

    @NotNull
    private LocalDate dispatchDate;

    @NotNull
    private CustomerType customerType;

    @Size(max = 80)
    private String customerId;

    @NotBlank
    private String customerName;

    @NotNull
    private ProductType productType;

    @NotNull
    @Positive
    private Double quantity;

    @NotNull
    @Positive
    private Double unitPrice;

    @PositiveOrZero
    private Double receivedAmount;

    @NotNull
    private PaymentMode paymentMode;

    private LocalDate batchDate;
    private Shift batchShift;
    private String notes;
    @Size(max = 80)
    private String routeName;

    @Size(max = 120)
    private String collectionPoint;

    private Double fatPercent;

    private Double snfPercent;

    private Double fatRatePerKg;

    private Double snfRatePerKg;

    private SettlementCycle settlementCycle;

    private Boolean overrideWithdrawalLock;

    @Size(max = 700)
    private String overrideReason;
}
