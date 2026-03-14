package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionStatementResponse {

    private String customerId;
    private String customerName;
    private String month;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private boolean subscriptionActive;
    private String pricingMode;

    private int cycleDays;
    private int baselinePlanDays;
    private int activePlanDays;
    private int pausedDays;
    private int skipDays;
    private int holidayWeekdayDays;
    private int billedDays;

    private double prorationFactor;
    private double baselinePlanQty;
    private double baselinePlanAmount;
    private double plannedQty;
    private double plannedAmount;
    private double holidayCreditAmount;
    private double billedQty;
    private double billedAmount;
    private double receivedAmount;
    private double pendingAmount;
    private double expectedVsBilledVariance;

    private double currentRunningBalance;
    private double totalPaidToDate;

    private List<CustomerSubscriptionStatementDailyRowResponse> dailyRows;
}
