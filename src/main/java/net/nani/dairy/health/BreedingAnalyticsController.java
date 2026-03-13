package net.nani.dairy.health;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.health.dto.BreedingKpiSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/breeding")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BreedingAnalyticsController {

    private final BreedingAnalyticsService breedingAnalyticsService;

    @GetMapping("/kpis")
    public BreedingKpiSummaryResponse kpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "6") Integer trendMonths
    ) {
        return breedingAnalyticsService.kpis(date, trendMonths);
    }
}
