package net.nani.dairy.sales.dto;

import java.time.LocalDate;

public record SalesSummaryResponse(
        LocalDate date,
        double totalRevenue,
        double milkRevenue,
        double otherRevenue,
        double totalReceived,
        double totalPending,
        long totalTransactions
) {
}
