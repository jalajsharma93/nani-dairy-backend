package net.nani.dairy.sales.dto;

import lombok.*;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.PaymentMode;
import net.nani.dairy.sales.PaymentStatus;
import net.nani.dairy.sales.ProductType;
import net.nani.dairy.sales.SettlementCycle;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponse {
    private String saleId;
    private LocalDate dispatchDate;
    private CustomerType customerType;
    private String customerName;
    private ProductType productType;
    private double quantity;
    private double unitPrice;
    private Double baseUnitPrice;
    private String routeName;
    private String collectionPoint;
    private Double fatPercent;
    private Double snfPercent;
    private Double fatRatePerKg;
    private Double snfRatePerKg;
    private boolean qualityPricingApplied;
    private SettlementCycle settlementCycle;
    private boolean reconciled;
    private OffsetDateTime reconciledAt;
    private String reconciledBy;
    private String reconciliationNote;
    private boolean delivered;
    private OffsetDateTime deliveredAt;
    private String deliveredBy;
    private String deliveryNote;
    private double totalAmount;
    private double receivedAmount;
    private double pendingAmount;
    private PaymentStatus paymentStatus;
    private PaymentMode paymentMode;
    private LocalDate batchDate;
    private Shift batchShift;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
