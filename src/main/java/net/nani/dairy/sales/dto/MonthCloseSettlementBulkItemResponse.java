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
public class MonthCloseSettlementBulkItemResponse {

    private CustomerType customerType;
    private String customerId;
    private String customerName;
    private boolean success;
    private String message;
    private boolean reconciliationApplied;
    private int reconciledSales;
    private double payoutRecorded;
    private Double customerBalanceAfter;
}
