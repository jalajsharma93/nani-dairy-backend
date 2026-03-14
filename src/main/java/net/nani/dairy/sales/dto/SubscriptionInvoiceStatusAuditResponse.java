package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.SubscriptionInvoiceStatus;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionInvoiceStatusAuditResponse {
    private String subscriptionInvoiceStatusAuditId;
    private String customerId;
    private String customerName;
    private CustomerType customerType;
    private String month;
    private String invoiceNumber;
    private SubscriptionInvoiceStatus previousStatus;
    private SubscriptionInvoiceStatus currentStatus;
    private String action;
    private String statusNote;
    private String overrideReason;
    private boolean exceptionOverride;
    private String approvedBy;
    private String actorUsername;
    private OffsetDateTime createdAt;
}

