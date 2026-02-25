package net.nani.dairy.sales;

import jakarta.persistence.*;
import lombok.*;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEntity {

    @Id
    @Column(name = "sale_id", nullable = false, length = 80)
    private String saleId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 30)
    private CustomerType customerType;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "base_unit_price")
    private Double baseUnitPrice;

    @Column(name = "route_name", length = 80)
    private String routeName;

    @Column(name = "collection_point", length = 120)
    private String collectionPoint;

    @Column(name = "fat_percent")
    private Double fatPercent;

    @Column(name = "snf_percent")
    private Double snfPercent;

    @Column(name = "fat_rate_per_kg")
    private Double fatRatePerKg;

    @Column(name = "snf_rate_per_kg")
    private Double snfRatePerKg;

    @Column(name = "quality_pricing_applied")
    private Boolean qualityPricingApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_cycle", length = 20)
    private SettlementCycle settlementCycle;

    @Column(name = "is_reconciled")
    private Boolean reconciled;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "reconciled_by", length = 100)
    private String reconciledBy;

    @Column(name = "reconciliation_note", length = 500)
    private String reconciliationNote;

    @Column(name = "is_delivered")
    private Boolean delivered;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "delivered_by", length = 100)
    private String deliveredBy;

    @Column(name = "delivery_note", length = 300)
    private String deliveryNote;

    @Column(name = "total_amount", nullable = false)
    private double totalAmount;

    @Column(name = "received_amount", nullable = false)
    private double receivedAmount;

    @Column(name = "pending_amount", nullable = false)
    private double pendingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode;

    @Column(name = "batch_date")
    private LocalDate batchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_shift", length = 10)
    private Shift batchShift;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (paymentStatus == null) paymentStatus = PaymentStatus.UNPAID;
        if (paymentMode == null) paymentMode = PaymentMode.CASH;
        if (qualityPricingApplied == null) qualityPricingApplied = false;
        if (reconciled == null) reconciled = false;
        if (delivered == null) delivered = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
