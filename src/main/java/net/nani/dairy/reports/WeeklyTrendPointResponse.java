package net.nani.dairy.reports;

import java.time.LocalDate;

public record WeeklyTrendPointResponse(
        LocalDate date,
        double totalLiters,
        long passBatches,
        long totalBatches,
        double passRate
) {
}
