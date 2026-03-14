package net.nani.dairy.reports;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.expenses.ExpenseCategory;
import net.nani.dairy.expenses.ExpenseRepository;
import net.nani.dairy.feed.FeedLogRepository;
import net.nani.dairy.health.MedicalTreatmentRepository;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.milk.MilkEntryEntity;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.ProductType;
import net.nani.dairy.sales.SaleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MilkBatchRepository milkBatchRepository;
    private final MilkEntryRepository milkEntryRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final FeedLogRepository feedLogRepository;
    private final MedicalTreatmentRepository medicalTreatmentRepository;
    private final AnimalRepository animalRepository;

    public DailyReportResponse daily(LocalDate date) {
        var am = milkBatchRepository.findByDateAndShift(date, Shift.AM).orElse(null);
        var pm = milkBatchRepository.findByDateAndShift(date, Shift.PM).orElse(null);

        double amLiters = am != null ? am.getTotalLiters() : 0;
        double pmLiters = pm != null ? pm.getTotalLiters() : 0;

        long passBatches = 0;
        long holdBatches = 0;
        long rejectBatches = 0;

        if (am != null) {
            if (am.getQcStatus() == QcStatus.PASS) {
                passBatches++;
            }
            if (am.getQcStatus() == QcStatus.HOLD) {
                holdBatches++;
            }
            if (am.getQcStatus() == QcStatus.REJECT) {
                rejectBatches++;
            }
        }
        if (pm != null) {
            if (pm.getQcStatus() == QcStatus.PASS) {
                passBatches++;
            }
            if (pm.getQcStatus() == QcStatus.HOLD) {
                holdBatches++;
            }
            if (pm.getQcStatus() == QcStatus.REJECT) {
                rejectBatches++;
            }
        }

        var amEntries = milkEntryRepository.findByDateAndShift(date, Shift.AM);
        var pmEntries = milkEntryRepository.findByDateAndShift(date, Shift.PM);

        long cowsQcDone = 0;
        long cowsQcPending = 0;

        for (var e : amEntries) {
            if (e.getQcStatus() == QcStatus.PENDING) {
                cowsQcPending++;
            } else {
                cowsQcDone++;
            }
        }
        for (var e : pmEntries) {
            if (e.getQcStatus() == QcStatus.PENDING) {
                cowsQcPending++;
            } else {
                cowsQcDone++;
            }
        }

        return new DailyReportResponse(
                date,
                amLiters,
                pmLiters,
                amLiters + pmLiters,
                passBatches,
                holdBatches,
                rejectBatches,
                cowsQcDone,
                cowsQcPending
        );
    }

    public WeeklyTrendResponse weekly(LocalDate endDate, int days) {
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate startDate = endDate.minusDays(safeDays - 1L);

        List<WeeklyTrendPointResponse> points = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            DailyReportResponse daily = daily(date);

            long totalBatches = daily.passBatches() + daily.holdBatches() + daily.rejectBatches();
            double passRate = totalBatches > 0 ? (double) daily.passBatches() / totalBatches : 0;

            points.add(new WeeklyTrendPointResponse(
                    date,
                    daily.totalLiters(),
                    daily.passBatches(),
                    totalBatches,
                    passRate
            ));
        }

        return new WeeklyTrendResponse(startDate, endDate, points);
    }

    public AnimalProfitabilityResponse animalProfitability(String animalId, LocalDate toDate, Integer days) {
        String normalizedAnimalId = animalId == null ? "" : animalId.trim();
        if (normalizedAnimalId.isEmpty()) {
            throw new IllegalArgumentException("animalId is required");
        }
        if (!animalRepository.existsById(normalizedAnimalId)) {
            throw new IllegalArgumentException("Animal not found");
        }

        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now();
        int safeDays = days == null ? 30 : Math.max(7, Math.min(365, days));
        return computeAnimalProfitability(normalizedAnimalId, effectiveToDate, safeDays);
    }

    public HerdProfitabilityResponse herdProfitability(
            LocalDate toDate,
            Integer days,
            Boolean activeOnly,
            AnimalStatus status,
            Integer limit
    ) {
        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now();
        int safeDays = days == null ? 30 : Math.max(7, Math.min(365, days));
        LocalDate fromDate = effectiveToDate.minusDays(safeDays - 1L);
        int safeLimit = limit == null ? 25 : Math.max(1, Math.min(200, limit));

        List<AnimalEntity> candidates = resolveAnimals(activeOnly, status);
        if (candidates.isEmpty()) {
            return HerdProfitabilityResponse.builder()
                    .fromDate(fromDate)
                    .toDate(effectiveToDate)
                    .windowDays(safeDays)
                    .totalAnimals(0)
                    .positiveAnimals(0)
                    .negativeAnimals(0)
                    .cullingReviewCount(0)
                    .totalEstimatedRevenue(0.0)
                    .totalEstimatedCost(0.0)
                    .totalEstimatedNet(0.0)
                    .avgRoiPercent(null)
                    .items(List.of())
                    .build();
        }

        List<HerdProfitabilityItemResponse> items = new ArrayList<>();
        double totalEstimatedRevenue = 0.0;
        double totalEstimatedCost = 0.0;
        double totalEstimatedNet = 0.0;
        int positiveAnimals = 0;
        int negativeAnimals = 0;
        int cullingReviewCount = 0;
        double roiTotal = 0.0;
        int roiCount = 0;

        for (AnimalEntity animal : candidates) {
            AnimalProfitabilityResponse analytics = computeAnimalProfitability(
                    animal.getAnimalId(),
                    effectiveToDate,
                    safeDays
            );

            totalEstimatedRevenue += analytics.getEstimatedRevenue();
            totalEstimatedCost += analytics.getEstimatedTotalCost();
            totalEstimatedNet += analytics.getEstimatedNet();
            if (analytics.getEstimatedNet() >= 0) {
                positiveAnimals += 1;
            } else {
                negativeAnimals += 1;
            }
            if (analytics.isCullingReviewSuggested()) {
                cullingReviewCount += 1;
            }
            if (analytics.getRoiPercent() != null) {
                roiTotal += analytics.getRoiPercent();
                roiCount += 1;
            }

            items.add(HerdProfitabilityItemResponse.builder()
                    .animalId(animal.getAnimalId())
                    .tag(animal.getTag())
                    .name(animal.getName())
                    .breed(animal.getBreed())
                    .status(animal.getStatus())
                    .active(animal.isActive())
                    .animalMilkLiters(analytics.getAnimalMilkLiters())
                    .avgMilkPerDay(analytics.getAvgMilkPerDay())
                    .animalFeedKg(analytics.getAnimalFeedKg())
                    .animalTreatmentCount(analytics.getAnimalTreatmentCount())
                    .estimatedRevenue(analytics.getEstimatedRevenue())
                    .estimatedFeedCost(analytics.getEstimatedFeedCost())
                    .estimatedTreatmentCost(analytics.getEstimatedTreatmentCost())
                    .estimatedLaborCost(analytics.getEstimatedLaborCost())
                    .estimatedTotalCost(analytics.getEstimatedTotalCost())
                    .estimatedNet(analytics.getEstimatedNet())
                    .roiPercent(analytics.getRoiPercent())
                    .confidence(analytics.getConfidence())
                    .cullingReviewSuggested(analytics.isCullingReviewSuggested())
                    .recommendation(analytics.getRecommendation())
                    .warnings(analytics.getWarnings())
                    .build());
        }

        items.sort(
                Comparator.comparingDouble(HerdProfitabilityItemResponse::getEstimatedNet).reversed()
                        .thenComparing(Comparator.comparingDouble(HerdProfitabilityItemResponse::getAvgMilkPerDay).reversed())
                        .thenComparing(row -> row.getTag() == null ? "" : row.getTag(), String.CASE_INSENSITIVE_ORDER)
        );

        if (items.size() > safeLimit) {
            items = new ArrayList<>(items.subList(0, safeLimit));
        }

        return HerdProfitabilityResponse.builder()
                .fromDate(fromDate)
                .toDate(effectiveToDate)
                .windowDays(safeDays)
                .totalAnimals(candidates.size())
                .positiveAnimals(positiveAnimals)
                .negativeAnimals(negativeAnimals)
                .cullingReviewCount(cullingReviewCount)
                .totalEstimatedRevenue(totalEstimatedRevenue)
                .totalEstimatedCost(totalEstimatedCost)
                .totalEstimatedNet(totalEstimatedNet)
                .avgRoiPercent(roiCount > 0 ? roiTotal / roiCount : null)
                .items(items)
                .build();
    }

    private List<AnimalEntity> resolveAnimals(Boolean activeOnly, AnimalStatus status) {
        boolean onlyActive = activeOnly == null || activeOnly;
        if (status != null) {
            return onlyActive
                    ? animalRepository.findByIsActiveAndStatus(true, status)
                    : animalRepository.findByStatus(status);
        }
        return onlyActive ? animalRepository.findByIsActive(true) : animalRepository.findAll();
    }

    private AnimalProfitabilityResponse computeAnimalProfitability(
            String normalizedAnimalId,
            LocalDate effectiveToDate,
            int safeDays
    ) {
        LocalDate fromDate = effectiveToDate.minusDays(safeDays - 1L);

        List<MilkEntryEntity> animalMilkRows = milkEntryRepository.findByAnimalIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                normalizedAnimalId,
                fromDate,
                effectiveToDate
        );
        List<MilkEntryEntity> farmMilkRows = milkEntryRepository.findByDateBetween(fromDate, effectiveToDate);

        double animalMilkLiters = animalMilkRows.stream().mapToDouble(MilkEntryEntity::getLiters).sum();
        double totalMilkLiters = farmMilkRows.stream().mapToDouble(MilkEntryEntity::getLiters).sum();
        double avgMilkPerDay = safeDays > 0 ? animalMilkLiters / safeDays : 0;

        var milkSalesRows = saleRepository.findByDispatchDateBetween(fromDate, effectiveToDate).stream()
                .filter(row -> row.getProductType() == ProductType.MILK && row.getQuantity() > 0)
                .toList();
        double soldMilkQty = milkSalesRows.stream().mapToDouble(row -> row.getQuantity()).sum();
        double soldMilkRevenue = milkSalesRows.stream().mapToDouble(row -> row.getTotalAmount()).sum();
        double avgMilkPrice = soldMilkQty > 0 ? soldMilkRevenue / soldMilkQty : 0;
        double estimatedRevenue = animalMilkLiters * avgMilkPrice;

        var expenseRows = expenseRepository.findByExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(fromDate, effectiveToDate);
        double totalFeedExpense = expenseRows.stream()
                .filter(row -> row.getCategory() == ExpenseCategory.FEED)
                .mapToDouble(row -> row.getAmount())
                .sum();
        double totalVeterinaryExpense = expenseRows.stream()
                .filter(row -> row.getCategory() == ExpenseCategory.VETERINARY)
                .mapToDouble(row -> row.getAmount())
                .sum();
        double totalLaborExpense = expenseRows.stream()
                .filter(row -> row.getCategory() == ExpenseCategory.SALARY)
                .mapToDouble(row -> row.getAmount())
                .sum();

        var feedRows = feedLogRepository.findByFeedDateBetweenOrderByFeedDateAscCreatedAtAsc(fromDate, effectiveToDate);
        double totalFeedKg = feedRows.stream().mapToDouble(row -> row.getQuantityKg()).sum();
        double animalFeedKg = feedRows.stream()
                .filter(row -> normalizedAnimalId.equals(row.getAnimalId()))
                .mapToDouble(row -> row.getQuantityKg())
                .sum();
        double feedCostPerKg = totalFeedKg > 0 ? totalFeedExpense / totalFeedKg : 0;
        double estimatedFeedCost = animalFeedKg * feedCostPerKg;

        var treatmentRows = medicalTreatmentRepository.findByTreatmentDateBetweenOrderByTreatmentDateAscCreatedAtAsc(
                fromDate,
                effectiveToDate
        );
        int totalTreatmentCount = treatmentRows.size();
        int animalTreatmentCount = (int) treatmentRows.stream()
                .filter(row -> normalizedAnimalId.equals(row.getAnimalId()))
                .count();
        double treatmentCostPerCase = totalTreatmentCount > 0 ? totalVeterinaryExpense / totalTreatmentCount : 0;
        double estimatedTreatmentCost = animalTreatmentCount * treatmentCostPerCase;

        double laborCostPerLiter = totalMilkLiters > 0 ? totalLaborExpense / totalMilkLiters : 0;
        double estimatedLaborCost = animalMilkLiters * laborCostPerLiter;

        double estimatedTotalCost = estimatedFeedCost + estimatedTreatmentCost + estimatedLaborCost;
        double estimatedNet = estimatedRevenue - estimatedTotalCost;
        Double roiPercent;
        if (estimatedTotalCost > 0) {
            roiPercent = (estimatedNet / estimatedTotalCost) * 100.0;
        } else if (estimatedRevenue > 0) {
            roiPercent = 100.0;
        } else {
            roiPercent = null;
        }

        List<String> warnings = new ArrayList<>();
        if (soldMilkQty <= 0) {
            warnings.add("No milk sale records found in selected window.");
        }
        if (totalFeedKg <= 0 && totalFeedExpense > 0) {
            warnings.add("Feed expense exists but feed quantity logs are missing.");
        }
        if (totalTreatmentCount <= 0 && totalVeterinaryExpense > 0) {
            warnings.add("Veterinary expense exists but treatment records are missing.");
        }
        if (totalMilkLiters <= 0 && totalLaborExpense > 0) {
            warnings.add("Salary expense exists but milk entries are missing.");
        }
        if (animalMilkLiters <= 0) {
            warnings.add("No milk entries found for this animal in selected window.");
        }

        String confidence = soldMilkQty > 0 && totalFeedKg > 0 && totalTreatmentCount > 0 && totalMilkLiters > 0
                ? "HIGH"
                : soldMilkQty > 0 && (totalFeedKg > 0 || totalTreatmentCount > 0 || totalMilkLiters > 0)
                ? "MEDIUM"
                : "LOW";

        boolean cullingReviewSuggested = estimatedNet < 0 && avgMilkPerDay < 5;
        String recommendation;
        if (cullingReviewSuggested) {
            recommendation = "Negative contribution with low yield. Review ration and consider culling if this trend continues.";
        } else if (estimatedNet < 0) {
            recommendation = "Negative contribution. Review feed plan, health issues and milk quality.";
        } else if (estimatedNet > 0 && avgMilkPerDay >= 8) {
            recommendation = "Healthy contribution. Continue current plan and monitor consistency.";
        } else {
            recommendation = "Moderate positive contribution. Track another 2-4 weeks before major decisions.";
        }

        return AnimalProfitabilityResponse.builder()
                .animalId(normalizedAnimalId)
                .fromDate(fromDate)
                .toDate(effectiveToDate)
                .windowDays(safeDays)
                .avgMilkPrice(avgMilkPrice)
                .animalMilkLiters(animalMilkLiters)
                .totalMilkLiters(totalMilkLiters)
                .avgMilkPerDay(avgMilkPerDay)
                .animalFeedKg(animalFeedKg)
                .animalTreatmentCount(animalTreatmentCount)
                .estimatedRevenue(estimatedRevenue)
                .estimatedFeedCost(estimatedFeedCost)
                .estimatedTreatmentCost(estimatedTreatmentCost)
                .estimatedLaborCost(estimatedLaborCost)
                .estimatedTotalCost(estimatedTotalCost)
                .estimatedNet(estimatedNet)
                .roiPercent(roiPercent)
                .feedCostPerKg(feedCostPerKg)
                .treatmentCostPerCase(treatmentCostPerCase)
                .laborCostPerLiter(laborCostPerLiter)
                .confidence(confidence)
                .cullingReviewSuggested(cullingReviewSuggested)
                .recommendation(recommendation)
                .warnings(warnings)
                .build();
    }
}
