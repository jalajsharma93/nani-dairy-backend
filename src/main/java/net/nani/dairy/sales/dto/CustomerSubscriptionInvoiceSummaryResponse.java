package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.SubscriptionInvoiceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionInvoiceSummaryResponse {

    private String customerId;
    private String customerName;
    private CustomerType customerType;
    private String routeName;
    private String month;
    private String invoiceNumber;
    private SubscriptionInvoiceStatus status;
    private OffsetDateTime lastStatusUpdatedAt;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private double plannedAmount;
    private double holidayCreditAmount;
    private double billedAmount;
    private double receivedAmount;
    private double pendingAmount;
    private double openingPendingAmount;
    private double closingPendingAmount;
    private double addOnBilledAmount;
    private double underDeliveryCreditAmount;
    private double prorationFactor;
}
