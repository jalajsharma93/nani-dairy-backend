package net.nani.dairy.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_subscription_invoice", indexes = {
        @Index(name = "idx_sub_inv_customer", columnList = "customer_id"),
        @Index(name = "idx_sub_inv_month", columnList = "invoice_month"),
        @Index(name = "idx_sub_inv_status", columnList = "invoice_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionInvoiceEntity {

    @Id
    @Column(name = "subscription_invoice_id", length = 80, nullable = false)
    private String subscriptionInvoiceId;

    @Column(name = "customer_id", length = 80, nullable = false)
    private String customerId;

    @Column(name = "customer_name", length = 120, nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 30, nullable = false)
    private CustomerType customerType;

    @Column(name = "route_name", length = 80)
    private String routeName;

    @Column(name = "collection_point", length = 120)
    private String collectionPoint;

    @Column(name = "invoice_month", length = 7, nullable = false)
    private String invoiceMonth;

    @Column(name = "invoice_number", length = 40, nullable = false)
    private String invoiceNumber;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "subscription_active", nullable = false)
    private boolean subscriptionActive;

    @Column(name = "pricing_mode", length = 30)
    private String pricingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_status", length = 20, nullable = false)
    private SubscriptionInvoiceStatus status;

    @Column(name = "proration_factor", nullable = false)
    private double prorationFactor;

    @Column(name = "cycle_days", nullable = false)
    private int cycleDays;

    @Column(name = "active_plan_days", nullable = false)
    private int activePlanDays;

    @Column(name = "paused_days", nullable = false)
    private int pausedDays;

    @Column(name = "skip_days", nullable = false)
    private int skipDays;

    @Column(name = "billed_days", nullable = false)
    private int billedDays;

    @Column(name = "planned_qty", nullable = false)
    private double plannedQty;

    @Column(name = "planned_amount", nullable = false)
    private double plannedAmount;

    @Column(name = "holiday_credit_amount", nullable = false)
    private double holidayCreditAmount;

    @Column(name = "billed_qty", nullable = false)
    private double billedQty;

    @Column(name = "billed_amount", nullable = false)
    private double billedAmount;

    @Column(name = "received_amount", nullable = false)
    private double receivedAmount;

    @Column(name = "pending_amount", nullable = false)
    private double pendingAmount;

    @Column(name = "add_on_billed_amount", nullable = false)
    private double addOnBilledAmount;

    @Column(name = "under_delivery_credit_amount", nullable = false)
    private double underDeliveryCreditAmount;

    @Column(name = "opening_pending_amount", nullable = false)
    private double openingPendingAmount;

    @Column(name = "closing_pending_amount", nullable = false)
    private double closingPendingAmount;

    @Column(name = "current_running_balance", nullable = false)
    private double currentRunningBalance;

    @Column(name = "status_note", length = 700)
    private String statusNote;

    @Column(name = "last_status_updated_at")
    private OffsetDateTime lastStatusUpdatedAt;

    @Column(name = "last_status_updated_by", length = 100)
    private String lastStatusUpdatedBy;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "finalized_by", length = 100)
    private String finalizedBy;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "posted_by", length = 100)
    private String postedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SubscriptionInvoiceStatus.DRAFT;
        }
        if (lastStatusUpdatedAt == null) {
            lastStatusUpdatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
