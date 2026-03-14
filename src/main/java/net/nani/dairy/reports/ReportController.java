package net.nani.dairy.reports;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/animals/{animalId}/profitability")
    public AnimalProfitabilityResponse animalProfitability(
            @PathVariable String animalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "30") Integer days
    ) {
        return reportService.animalProfitability(animalId, toDate, days);
    }

    @GetMapping("/animals/profitability")
    public HerdProfitabilityResponse herdProfitability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly,
            @RequestParam(required = false) AnimalStatus status,
            @RequestParam(required = false, defaultValue = "25") Integer limit
    ) {
        return reportService.herdProfitability(toDate, days, activeOnly, status, limit);
    }
}
