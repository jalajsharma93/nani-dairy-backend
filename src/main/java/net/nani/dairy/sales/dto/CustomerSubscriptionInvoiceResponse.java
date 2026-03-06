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
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionInvoiceResponse {

    private String customerId;
    private String customerName;
    private CustomerType customerType;
    private String routeName;
    private String collectionPoint;

    private String month;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private SubscriptionInvoiceStatus status;
    private String statusNote;
    private OffsetDateTime lastStatusUpdatedAt;
    private String lastStatusUpdatedBy;
    private OffsetDateTime finalizedAt;
    private String finalizedBy;
    private OffsetDateTime postedAt;
    private String postedBy;

    private boolean subscriptionActive;
    private String pricingMode;
    private double prorationFactor;

    private int cycleDays;
    private int activePlanDays;
    private int pausedDays;
    private int skipDays;
    private int billedDays;

    private double plannedQty;
    private double plannedAmount;
    private double holidayCreditAmount;
    private double billedQty;
    private double billedAmount;
    private double receivedAmount;
    private double pendingAmount;

    private double addOnBilledAmount;
    private double underDeliveryCreditAmount;
    private double openingPendingAmount;
    private double closingPendingAmount;
    private double currentRunningBalance;

    private List<CustomerSubscriptionInvoiceLineItemResponse> invoiceLineItems;
    private List<CustomerSubscriptionStatementDailyRowResponse> dailyRows;
}
