package net.nani.dairy.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public DailyReportResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reportService.daily(date != null ? date : LocalDate.now());
    }

    @GetMapping("/weekly")
    public WeeklyTrendResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "7") Integer days
    ) {
        LocalDate endDate = date != null ? date : LocalDate.now();
        int safeDays = days == null ? 7 : Math.max(1, Math.min(30, days));
        return reportService.weekly(endDate, safeDays);
    }
}
