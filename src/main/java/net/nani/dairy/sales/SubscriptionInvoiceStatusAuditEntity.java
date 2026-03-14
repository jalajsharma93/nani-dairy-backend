package net.nani.dairy.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "subscription_invoice_status_audit", indexes = {
        @Index(name = "idx_sub_inv_audit_month", columnList = "invoice_month"),
        @Index(name = "idx_sub_inv_audit_customer", columnList = "customer_id"),
        @Index(name = "idx_sub_inv_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionInvoiceStatusAuditEntity {

    @Id
    @Column(name = "subscription_invoice_status_audit_id", length = 80, nullable = false)
    private String subscriptionInvoiceStatusAuditId;

    @Column(name = "customer_id", length = 80, nullable = false)
    private String customerId;

    @Column(name = "customer_name", length = 120, nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 30, nullable = false)
    private CustomerType customerType;

    @Column(name = "invoice_month", length = 7, nullable = false)
    private String invoiceMonth;

    @Column(name = "invoice_number", length = 40, nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, nullable = false)
    private SubscriptionInvoiceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 20, nullable = false)
    private SubscriptionInvoiceStatus currentStatus;

    @Column(name = "action_name", length = 20, nullable = false)
    private String action;

    @Column(name = "status_note", length = 700)
    private String statusNote;

    @Column(name = "override_reason", length = 700)
    private String overrideReason;

    @Column(name = "exception_override", nullable = false)
    private boolean exceptionOverride;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "actor_username", length = 100, nullable = false)
    private String actorUsername;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

