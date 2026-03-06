package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.SubscriptionInvoiceStatus;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionInvoiceStatusUpdateResponse {

    private String customerId;
    private String month;
    private String invoiceNumber;
    private SubscriptionInvoiceStatus previousStatus;
    private SubscriptionInvoiceStatus currentStatus;
    private String statusNote;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
