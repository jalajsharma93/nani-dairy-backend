package net.nani.dairy.sales.dto;

import net.nani.dairy.sales.CustomerType;

public record CustomerLedgerRowResponse(
        String customerName,
        CustomerType customerType,
        double totalAmount,
        double totalReceived,
        double totalPending,
        double totalQuantity,
        long totalTransactions
) {
}
