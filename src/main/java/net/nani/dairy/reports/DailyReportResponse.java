package net.nani.dairy.reports;

import java.time.LocalDate;

public record DailyReportResponse(
        LocalDate date,
        double amLiters,
        double pmLiters,
        double totalLiters,
        long passBatches,
        long holdBatches,
        long rejectBatches,
        long cowsQcDone,
        long cowsQcPending
) {
}
