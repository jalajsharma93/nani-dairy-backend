package net.nani.dairy.health;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.health.dto.BreedingKpiPointResponse;
import net.nani.dairy.health.dto.BreedingKpiSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BreedingAnalyticsService {

    private static final int PREGNANCY_CHECK_DUE_DAYS = 60;

    private final BreedingEventRepository breedingEventRepository;

    public BreedingKpiSummaryResponse kpis(LocalDate date, Integer trendMonths) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeTrendMonths = sanitizeTrendMonths(trendMonths);

        YearMonth endMonth = YearMonth.from(effectiveDate);
        YearMonth startMonth = endMonth.minusMonths(safeTrendMonths - 1L);
        LocalDate analysisFromDate = startMonth.atDay(1);

        List<BreedingEventEntity> rows = breedingEventRepository.findAll();

        List<BreedingEventEntity> checkedRows = rows.stream()
                .filter(row -> row.getPregnancyCheckDate() != null)
                .filter(row -> !row.getPregnancyCheckDate().isBefore(analysisFromDate))
                .filter(row -> !row.getPregnancyCheckDate().isAfter(effectiveDate))
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.PREGNANT
                        || row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                .toList();

        long pregnantConfirmed = checkedRows.stream()
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.PREGNANT)
                .count();
        long notPregnantConfirmed = checkedRows.stream()
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                .count();
        long pregnancyChecksTotal = pregnantConfirmed + notPregnantConfirmed;

        LocalDate repeatWindowFrom = effectiveDate.minusDays(365);
        List<BreedingEventEntity> failedRowsLast365Days = rows.stream()
                .filter(row -> row.getInseminationDate() != null)
                .filter(row -> !row.getInseminationDate().isBefore(repeatWindowFrom))
                .filter(row -> !row.getInseminationDate().isAfter(effectiveDate))
                .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                .toList();

        Map<String, Long> failedByAnimal = countByAnimal(failedRowsLast365Days);
        long repeatBreederAnimals = failedByAnimal.values().stream().filter(count -> count >= 3L).count();
        long repeatBreederAtRiskAnimals = failedByAnimal.values().stream().filter(count -> count == 2L).count();

        List<LocalDate> pendingPregCheckDueDates = rows.stream()
                .filter(row -> row.getInseminationDate() != null)
                .filter(row -> row.getPregnancyCheckDate() == null)
                .filter(row -> row.getActualCalvingDate() == null)
                .map(row -> row.getInseminationDate().plusDays(PREGNANCY_CHECK_DUE_DAYS))
                .toList();

        LocalDate dueSoonEnd = effectiveDate.plusDays(7);
        long overduePregnancyChecks = pendingPregCheckDueDates.stream()
                .filter(dueDate -> dueDate.isBefore(effectiveDate))
                .count();
        long dueSoonPregnancyChecks = pendingPregCheckDueDates.stream()
                .filter(dueDate -> !dueDate.isBefore(effectiveDate) && !dueDate.isAfter(dueSoonEnd))
                .count();

        Double avgHeatToInseminationDays = averageDays(
                rows,
                analysisFromDate,
                effectiveDate,
                BreedingEventEntity::getHeatDate,
                BreedingEventEntity::getInseminationDate
        );
        Double avgInseminationToPregCheckDays = averageDays(
                rows,
                analysisFromDate,
                effectiveDate,
                BreedingEventEntity::getInseminationDate,
                BreedingEventEntity::getPregnancyCheckDate
        );
        Double avgHeatToPregCheckDays = averageDays(
                rows,
                analysisFromDate,
                effectiveDate,
                BreedingEventEntity::getHeatDate,
                BreedingEventEntity::getPregnancyCheckDate
        );

        List<BreedingKpiPointResponse> conceptionTrend = buildConceptionTrend(rows, startMonth, endMonth, effectiveDate);
        List<BreedingKpiPointResponse> repeatTrend = buildRepeatBreederTrend(rows, startMonth, endMonth, effectiveDate);
        List<BreedingKpiPointResponse> serviceTrend = buildServicePeriodTrend(rows, startMonth, endMonth, effectiveDate);

        return BreedingKpiSummaryResponse.builder()
                .date(effectiveDate)
                .trendMonths(safeTrendMonths)
                .conceptionRatePercent(percent(pregnantConfirmed, pregnancyChecksTotal))
                .pregnancyChecksTotal(pregnancyChecksTotal)
                .pregnantConfirmed(pregnantConfirmed)
                .notPregnantConfirmed(notPregnantConfirmed)
                .repeatBreederAnimals(repeatBreederAnimals)
                .repeatBreederAtRiskAnimals(repeatBreederAtRiskAnimals)
                .repeatBreederFailuresLast365Days((long) failedRowsLast365Days.size())
                .pendingPregnancyChecks((long) pendingPregCheckDueDates.size())
                .overduePregnancyChecks(overduePregnancyChecks)
                .dueSoonPregnancyChecks(dueSoonPregnancyChecks)
                .avgHeatToInseminationDays(avgHeatToInseminationDays)
                .avgInseminationToPregCheckDays(avgInseminationToPregCheckDays)
                .avgHeatToPregCheckDays(avgHeatToPregCheckDays)
                .conceptionTrend(conceptionTrend)
                .repeatBreederTrend(repeatTrend)
                .servicePeriodTrend(serviceTrend)
                .build();
    }

    private List<BreedingKpiPointResponse> buildConceptionTrend(
            List<BreedingEventEntity> rows,
            YearMonth startMonth,
            YearMonth endMonth,
            LocalDate effectiveDate
    ) {
        List<BreedingKpiPointResponse> points = new ArrayList<>();
        YearMonth month = startMonth;
        while (!month.isAfter(endMonth)) {
            LocalDate from = month.atDay(1);
            LocalDate to = min(month.atEndOfMonth(), effectiveDate);
            List<BreedingEventEntity> monthRows = rows.stream()
                    .filter(row -> row.getPregnancyCheckDate() != null)
                    .filter(row -> !row.getPregnancyCheckDate().isBefore(from))
                    .filter(row -> !row.getPregnancyCheckDate().isAfter(to))
                    .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.PREGNANT
                            || row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                    .toList();

            long pregnant = monthRows.stream()
                    .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.PREGNANT)
                    .count();
            long total = monthRows.size();

            points.add(BreedingKpiPointResponse.builder()
                    .month(month.toString())
                    .value(percent(pregnant, total))
                    .numerator(pregnant)
                    .denominator(total)
                    .build());
            month = month.plusMonths(1);
        }
        return points;
    }

    private List<BreedingKpiPointResponse> buildRepeatBreederTrend(
            List<BreedingEventEntity> rows,
            YearMonth startMonth,
            YearMonth endMonth,
            LocalDate effectiveDate
    ) {
        List<BreedingKpiPointResponse> points = new ArrayList<>();
        YearMonth month = startMonth;
        while (!month.isAfter(endMonth)) {
            LocalDate monthEnd = min(month.atEndOfMonth(), effectiveDate);
            LocalDate windowFrom = monthEnd.minusDays(365);

            List<BreedingEventEntity> failedRows = rows.stream()
                    .filter(row -> row.getInseminationDate() != null)
                    .filter(row -> !row.getInseminationDate().isBefore(windowFrom))
                    .filter(row -> !row.getInseminationDate().isAfter(monthEnd))
                    .filter(row -> row.getPregnancyResult() == BreedingPregnancyResult.NOT_PREGNANT)
                    .toList();

            Map<String, Long> failedByAnimal = countByAnimal(failedRows);
            long repeatAnimals = failedByAnimal.values().stream().filter(count -> count >= 3L).count();
            long activeAnimals = failedByAnimal.keySet().stream().filter(Objects::nonNull).count();

            points.add(BreedingKpiPointResponse.builder()
                    .month(month.toString())
                    .value((double) repeatAnimals)
                    .numerator(repeatAnimals)
                    .denominator(activeAnimals)
                    .build());
            month = month.plusMonths(1);
        }
        return points;
    }

    private List<BreedingKpiPointResponse> buildServicePeriodTrend(
            List<BreedingEventEntity> rows,
            YearMonth startMonth,
            YearMonth endMonth,
            LocalDate effectiveDate
    ) {
        List<BreedingKpiPointResponse> points = new ArrayList<>();
        YearMonth month = startMonth;
        while (!month.isAfter(endMonth)) {
            LocalDate from = month.atDay(1);
            LocalDate to = min(month.atEndOfMonth(), effectiveDate);

            long samples = 0L;
            long totalDays = 0L;
            for (BreedingEventEntity row : rows) {
                LocalDate inseminationDate = row.getInseminationDate();
                LocalDate pregnancyCheckDate = row.getPregnancyCheckDate();
                if (inseminationDate == null || pregnancyCheckDate == null) {
                    continue;
                }
                if (pregnancyCheckDate.isBefore(inseminationDate)) {
                    continue;
                }
                if (pregnancyCheckDate.isBefore(from) || pregnancyCheckDate.isAfter(to)) {
                    continue;
                }
                samples++;
                totalDays += ChronoUnit.DAYS.between(inseminationDate, pregnancyCheckDate);
            }

            double value = samples == 0L ? 0d : (double) totalDays / (double) samples;
            points.add(BreedingKpiPointResponse.builder()
                    .month(month.toString())
                    .value(roundTo2(value))
                    .numerator(totalDays)
                    .denominator(samples)
                    .build());
            month = month.plusMonths(1);
        }
        return points;
    }

    private Map<String, Long> countByAnimal(List<BreedingEventEntity> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (BreedingEventEntity row : rows) {
            String animalId = row.getAnimalId();
            if (animalId == null || animalId.isBlank()) {
                continue;
            }
            counts.merge(animalId, 1L, Long::sum);
        }
        return counts;
    }

    private Double averageDays(
            List<BreedingEventEntity> rows,
            LocalDate filterFrom,
            LocalDate filterTo,
            DateExtractor fromExtractor,
            DateExtractor toExtractor
    ) {
        long samples = 0L;
        long totalDays = 0L;

        for (BreedingEventEntity row : rows) {
            LocalDate from = fromExtractor.extract(row);
            LocalDate to = toExtractor.extract(row);
            if (from == null || to == null) {
                continue;
            }
            if (to.isBefore(from)) {
                continue;
            }
            if (to.isBefore(filterFrom) || to.isAfter(filterTo)) {
                continue;
            }
            samples++;
            totalDays += ChronoUnit.DAYS.between(from, to);
        }

        if (samples == 0L) {
            return null;
        }
        return roundTo2((double) totalDays / (double) samples);
    }

    private int sanitizeTrendMonths(Integer trendMonths) {
        if (trendMonths == null) {
            return 6;
        }
        return Math.max(3, Math.min(12, trendMonths));
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private Double percent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0d;
        }
        return roundTo2(((double) numerator * 100d) / (double) denominator);
    }

    private Double roundTo2(double value) {
        return Double.valueOf(String.format(Locale.US, "%.2f", value));
    }

    @FunctionalInterface
    private interface DateExtractor {
        LocalDate extract(BreedingEventEntity row);
    }
}
