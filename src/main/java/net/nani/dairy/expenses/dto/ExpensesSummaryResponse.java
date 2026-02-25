package net.nani.dairy.expenses.dto;

import java.time.LocalDate;

public record ExpensesSummaryResponse(
        LocalDate date,
        double totalAmount,
        double salaryAmount,
        double otherAmount,
        long totalTransactions
) {
}
