package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionStatementSummaryResponse {
    private String customerId;
    private String customerName;
    private CustomerType customerType;
    private String routeName;
    private String month;
    private String pricingMode;
    private double prorationFactor;
    private int activePlanDays;
    private int pausedDays;
    private int skipDays;
    private int holidayWeekdayDays;
    private int billedDays;
    private double plannedAmount;
    private double billedAmount;
    private double receivedAmount;
    private double pendingAmount;
    private double expectedVsBilledVariance;
    private double currentRunningBalance;
}
