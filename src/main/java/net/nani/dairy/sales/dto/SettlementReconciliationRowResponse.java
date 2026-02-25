package net.nani.dairy.sales.dto;

import net.nani.dairy.sales.CustomerType;
import net.nani.dairy.sales.SettlementCycle;

public record SettlementReconciliationRowResponse(
        String customerName,
        CustomerType customerType,
        String routeName,
        String collectionPoint,
        SettlementCycle settlementCycle,
        double totalAmount,
        double totalReceived,
        double totalPending,
        double totalQuantity,
        long totalTransactions,
        long reconciledTransactions,
        long unreconciledTransactions
) {
}
