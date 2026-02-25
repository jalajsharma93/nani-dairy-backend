package net.nani.dairy.reports;

import java.time.LocalDate;
import java.util.List;

public record WeeklyTrendResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<WeeklyTrendPointResponse> points
) {
}
