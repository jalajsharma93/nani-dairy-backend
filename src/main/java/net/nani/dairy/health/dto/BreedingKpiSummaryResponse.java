package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreedingKpiSummaryResponse {

    private LocalDate date;
    private Integer trendMonths;

    private Double conceptionRatePercent;
    private Long pregnancyChecksTotal;
    private Long pregnantConfirmed;
    private Long notPregnantConfirmed;

    private Long repeatBreederAnimals;
    private Long repeatBreederAtRiskAnimals;
    private Long repeatBreederFailuresLast365Days;

    private Long pendingPregnancyChecks;
    private Long overduePregnancyChecks;
    private Long dueSoonPregnancyChecks;

    private Double avgHeatToInseminationDays;
    private Double avgInseminationToPregCheckDays;
    private Double avgHeatToPregCheckDays;

    private List<BreedingKpiPointResponse> conceptionTrend;
    private List<BreedingKpiPointResponse> repeatBreederTrend;
    private List<BreedingKpiPointResponse> servicePeriodTrend;
}
