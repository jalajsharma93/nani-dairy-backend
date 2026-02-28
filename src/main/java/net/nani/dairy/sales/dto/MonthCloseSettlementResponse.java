package net.nani.dairy.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.sales.CustomerType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthCloseSettlementResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private CustomerType customerType;
    private String customerId;
    private String customerName;
    private boolean reconciliationApplied;
    private int reconciledSales;
    private double payoutRecorded;
    private Double customerBalanceAfter;
    private String closedBy;
    private OffsetDateTime closedAt;
    private String note;
}
