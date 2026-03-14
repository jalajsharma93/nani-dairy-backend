package net.nani.dairy.feed;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.dto.*;
import net.nani.dairy.milk.MilkEntryEntity;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.tasks.TaskAutomationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedManagementService {

    private final FeedMaterialRepository feedMaterialRepository;
    private final FeedRecipeRepository feedRecipeRepository;
    private final FeedSopTaskRepository feedSopTaskRepository;
    private final FeedLogRepository feedLogRepository;
    private final FeedProcurementRunRepository feedProcurementRunRepository;
    private final AnimalRepository animalRepository;
    private final MilkEntryRepository milkEntryRepository;
    private final AuthUserRepository authUserRepository;
    private final TaskAutomationService taskAutomationService;

    public FeedManagementSummaryResponse summary(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        List<FeedRecipeEntity> recipes = feedRecipeRepository.findAllByOrderByUpdatedAtDesc();
        List<FeedSopTaskEntity> tasks = feedSopTaskRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(effectiveDate);

        int lowStock = 0;
        double totalInventoryValue = 0;
        for (FeedMaterialEntity material : materials) {
            if (isLowStock(material)) {
                lowStock++;
            }
            if (material.getCostPerUnit() != null && material.getCostPerUnit() > 0) {
                totalInventoryValue += material.getAvailableQty() * material.getCostPerUnit();
            }
        }

        int openTasks = 0;
        int doneTasks = 0;
        for (FeedSopTaskEntity task : tasks) {
            if (task.getStatus() == FeedSopTaskStatus.DONE) {
                doneTasks++;
            } else {
                openTasks++;
            }
        }

        int activeRecipes = 0;
        for (FeedRecipeEntity recipe : recipes) {
            if (recipe.isActive()) {
                activeRecipes++;
            }
        }

        return FeedManagementSummaryResponse.builder()
                .date(effectiveDate)
                .totalMaterials(materials.size())
                .lowStockMaterials(lowStock)
                .activeRecipes(activeRecipes)
                .openTasks(openTasks)
                .doneTasksToday(doneTasks)
                .totalInventoryValue(totalInventoryValue)
                .build();
    }

    public List<FeedMaterialResponse> listMaterials(Boolean lowStockOnly) {
        var rows = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        if (Boolean.TRUE.equals(lowStockOnly)) {
            return rows.stream().filter(this::isLowStock).map(this::toMaterialResponse).toList();
        }
        return rows.stream().map(this::toMaterialResponse).toList();
    }

    public FeedInventoryForecastResponse forecast(LocalDate date, Integer lookbackDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeLookbackDays = lookbackDays == null ? 30 : Math.max(7, Math.min(120, lookbackDays));
        LocalDate fromDate = effectiveDate.minusDays(safeLookbackDays - 1L);

        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        List<FeedLogEntity> logs = feedLogRepository.findByFeedDateBetweenOrderByFeedDateAscCreatedAtAsc(fromDate, effectiveDate);

        Map<FeedMaterialCategory, Double> categoryUsageQty = new EnumMap<>(FeedMaterialCategory.class);
        for (FeedMaterialCategory category : FeedMaterialCategory.values()) {
            categoryUsageQty.put(category, 0.0);
        }
        for (FeedLogEntity row : logs) {
            FeedMaterialCategory mappedCategory = mapFeedTypeToCategory(row.getFeedType());
            categoryUsageQty.put(
                    mappedCategory,
                    categoryUsageQty.getOrDefault(mappedCategory, 0.0) + row.getQuantityKg()
            );
        }

        Map<FeedMaterialCategory, Long> materialsPerCategory = new EnumMap<>(FeedMaterialCategory.class);
        for (FeedMaterialCategory category : FeedMaterialCategory.values()) {
            materialsPerCategory.put(category, 0L);
        }
        for (FeedMaterialEntity material : materials) {
            materialsPerCategory.put(
                    material.getCategory(),
                    materialsPerCategory.getOrDefault(material.getCategory(), 0L) + 1L
            );
        }

        List<FeedInventoryForecastItemResponse> items = new ArrayList<>();
        int highRisk = 0;
        int mediumRisk = 0;
        int lowRisk = 0;
        double totalDailyUsageKg = logs.stream().mapToDouble(FeedLogEntity::getQuantityKg).sum() / safeLookbackDays;
        double totalRecommended30 = 0;
        double totalRecommended90 = 0;
        double totalCost30 = 0;
        double totalCost90 = 0;

        for (FeedMaterialEntity material : materials) {
            long categoryMaterials = Math.max(1, materialsPerCategory.getOrDefault(material.getCategory(), 1L));
            double categoryDailyUsage = categoryUsageQty.getOrDefault(material.getCategory(), 0.0) / safeLookbackDays;
            boolean logBased = material.getUnit() == FeedMaterialUnit.KG;
            double dailyUsage = logBased ? categoryDailyUsage / categoryMaterials : 0.0;

            double required30 = dailyUsage * 30.0;
            double required90 = dailyUsage * 90.0;
            double projected30 = material.getAvailableQty() - required30;
            double projected90 = material.getAvailableQty() - required90;
            double recommended30 = Math.max(0, required30 + material.getReorderLevelQty() - material.getAvailableQty());
            double recommended90 = Math.max(0, required90 + material.getReorderLevelQty() - material.getAvailableQty());
            if (!logBased) {
                recommended30 = Math.max(0, material.getReorderLevelQty() - material.getAvailableQty());
                recommended90 = recommended30;
                projected30 = material.getAvailableQty();
                projected90 = material.getAvailableQty();
            }

            Double daysLeft = dailyUsage > 0 ? material.getAvailableQty() / dailyUsage : null;
            String risk = "LOW";
            String recommendation = "Stock is healthy for near-term plan.";
            if (isLowStock(material) || projected30 < 0 || (daysLeft != null && daysLeft < 15)) {
                risk = "HIGH";
                recommendation = "Reorder now to avoid shortage in the next cycle.";
                highRisk++;
            } else if (projected90 < 0 || (daysLeft != null && daysLeft < 45)) {
                risk = "MEDIUM";
                recommendation = "Plan reorder in current month to avoid future shortage.";
                mediumRisk++;
            } else {
                lowRisk++;
            }

            totalRecommended30 += recommended30;
            totalRecommended90 += recommended90;
            if (material.getCostPerUnit() != null && material.getCostPerUnit() > 0) {
                totalCost30 += recommended30 * material.getCostPerUnit();
                totalCost90 += recommended90 * material.getCostPerUnit();
            }

            items.add(
                    FeedInventoryForecastItemResponse.builder()
                            .feedMaterialId(material.getFeedMaterialId())
                            .materialName(material.getMaterialName())
                            .category(material.getCategory())
                            .unit(material.getUnit())
                            .availableQty(material.getAvailableQty())
                            .reorderLevelQty(material.getReorderLevelQty())
                            .costPerUnit(material.getCostPerUnit())
                            .lowStock(isLowStock(material))
                            .estimatedDailyConsumptionQty(roundTo2(dailyUsage))
                            .daysOfStockLeft(daysLeft == null ? null : roundTo2(daysLeft))
                            .requiredQty30Days(roundTo2(required30))
                            .requiredQty90Days(roundTo2(required90))
                            .recommendedReorderQty30Days(roundTo2(recommended30))
                            .recommendedReorderQty90Days(roundTo2(recommended90))
                            .projectedStockAfter30Days(roundTo2(projected30))
                            .projectedStockAfter90Days(roundTo2(projected90))
                            .riskLevel(risk)
                            .recommendation(recommendation)
                            .forecastBasis(logBased ? "LOG_BASED" : "REORDER_LEVEL_ONLY")
                            .build()
            );
        }

        return FeedInventoryForecastResponse.builder()
                .date(effectiveDate)
                .lookbackDays(safeLookbackDays)
                .feedLogsCount(logs.size())
                .estimatedDailyConsumptionTotalKg(roundTo2(totalDailyUsageKg))
                .highRiskMaterials(highRisk)
                .mediumRiskMaterials(mediumRisk)
                .lowRiskMaterials(lowRisk)
                .totalRecommendedReorderQty30Days(roundTo2(totalRecommended30))
                .totalRecommendedReorderQty90Days(roundTo2(totalRecommended90))
                .totalRecommendedReorderCost30Days(roundTo2(totalCost30))
                .totalRecommendedReorderCost90Days(roundTo2(totalCost90))
                .items(items)
                .build();
    }

    public FeedProcurementPlanResponse procurementPlan(LocalDate date, Integer lookbackDays, Integer horizonDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeLookbackDays = lookbackDays == null ? 30 : Math.max(7, Math.min(120, lookbackDays));
        int safeHorizonDays = horizonDays == null ? 30 : (horizonDays >= 60 ? 90 : 30);

        FeedInventoryForecastResponse forecast = forecast(effectiveDate, safeLookbackDays);
        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        Map<String, FeedMaterialEntity> materialsById = new HashMap<>();
        for (FeedMaterialEntity material : materials) {
            materialsById.put(material.getFeedMaterialId(), material);
        }

        List<FeedProcurementPlanItemResponse> items = new ArrayList<>();
        int highUrgency = 0;
        int mediumUrgency = 0;
        int lowUrgency = 0;
        double totalRecommendedQty = 0;
        double totalEstimatedCost = 0;
        boolean hasEstimatedCost = false;

        List<FeedInventoryForecastItemResponse> forecastItems =
                forecast.getItems() == null ? List.of() : forecast.getItems();

        for (FeedInventoryForecastItemResponse row : forecastItems) {
            double recommendedQty = safeHorizonDays == 90
                    ? row.getRecommendedReorderQty90Days()
                    : row.getRecommendedReorderQty30Days();
            if (recommendedQty <= 0 && !row.isLowStock()) {
                continue;
            }

            FeedMaterialEntity material = materialsById.get(row.getFeedMaterialId());
            String supplier = material == null ? null : trimToNull(material.getSupplierName());
            String supplierName = supplier != null ? supplier : "UNASSIGNED";
            Double estimatedCost = row.getCostPerUnit() != null && row.getCostPerUnit() > 0
                    ? roundTo2(recommendedQty * row.getCostPerUnit())
                    : null;

            int urgencyScore = procurementUrgencyScore(row, recommendedQty);
            String urgencyLevel = procurementUrgencyLevel(urgencyScore);
            if ("HIGH".equals(urgencyLevel)) {
                highUrgency++;
            } else if ("MEDIUM".equals(urgencyLevel)) {
                mediumUrgency++;
            } else {
                lowUrgency++;
            }

            LocalDate orderByDate = suggestedOrderByDate(effectiveDate, urgencyLevel, row.getDaysOfStockLeft());

            String recommendation = row.getRecommendation();
            if (recommendation == null || recommendation.isBlank()) {
                recommendation = "Plan purchase based on demand window and current stock.";
            }
            if (supplier == null) {
                recommendation = recommendation + " Assign supplier before placing purchase order.";
            }
            if ("HIGH".equals(urgencyLevel)) {
                recommendation = recommendation + " Prioritize immediate order placement.";
            }

            items.add(
                    FeedProcurementPlanItemResponse.builder()
                            .rank(0)
                            .feedMaterialId(row.getFeedMaterialId())
                            .materialName(row.getMaterialName())
                            .category(row.getCategory())
                            .unit(row.getUnit())
                            .supplierName(supplierName)
                            .availableQty(roundTo2(row.getAvailableQty()))
                            .reorderLevelQty(roundTo2(row.getReorderLevelQty()))
                            .daysOfStockLeft(row.getDaysOfStockLeft())
                            .recommendedOrderQty(roundTo2(recommendedQty))
                            .estimatedOrderCost(estimatedCost)
                            .riskLevel(row.getRiskLevel())
                            .urgencyLevel(urgencyLevel)
                            .urgencyScore(urgencyScore)
                            .suggestedOrderByDate(orderByDate)
                            .forecastBasis(row.getForecastBasis())
                            .recommendation(recommendation)
                            .build()
            );

            totalRecommendedQty += recommendedQty;
            if (estimatedCost != null) {
                totalEstimatedCost += estimatedCost;
                hasEstimatedCost = true;
            }
        }

        items.sort(
                Comparator.comparingInt(FeedProcurementPlanItemResponse::getUrgencyScore).reversed()
                        .thenComparing(FeedProcurementPlanItemResponse::getSuggestedOrderByDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FeedProcurementPlanItemResponse::getRecommendedOrderQty, Comparator.reverseOrder())
                        .thenComparing(FeedProcurementPlanItemResponse::getMaterialName, String.CASE_INSENSITIVE_ORDER)
        );

        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }

        Map<String, List<FeedProcurementPlanItemResponse>> groupedBySupplier = new HashMap<>();
        for (FeedProcurementPlanItemResponse item : items) {
            String supplier = trimToNull(item.getSupplierName());
            String key = supplier == null ? "UNASSIGNED" : supplier;
            groupedBySupplier.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<FeedProcurementSupplierGroupResponse> supplierGroups = new ArrayList<>();
        for (Map.Entry<String, List<FeedProcurementPlanItemResponse>> entry : groupedBySupplier.entrySet()) {
            double groupQty = 0;
            double groupCost = 0;
            boolean groupHasCost = false;
            for (FeedProcurementPlanItemResponse item : entry.getValue()) {
                groupQty += item.getRecommendedOrderQty();
                if (item.getEstimatedOrderCost() != null) {
                    groupCost += item.getEstimatedOrderCost();
                    groupHasCost = true;
                }
            }

            supplierGroups.add(
                    FeedProcurementSupplierGroupResponse.builder()
                            .supplierName(entry.getKey())
                            .itemsCount(entry.getValue().size())
                            .totalRecommendedQty(roundTo2(groupQty))
                            .totalEstimatedCost(groupHasCost ? roundTo2(groupCost) : null)
                            .items(entry.getValue())
                            .build()
            );
        }

        supplierGroups.sort(
                Comparator.comparing(
                                (FeedProcurementSupplierGroupResponse row) ->
                                        row.getTotalEstimatedCost() == null ? -1.0 : row.getTotalEstimatedCost()
                        )
                        .reversed()
                        .thenComparing(FeedProcurementSupplierGroupResponse::getTotalRecommendedQty, Comparator.reverseOrder())
                        .thenComparing(FeedProcurementSupplierGroupResponse::getSupplierName, String.CASE_INSENSITIVE_ORDER)
        );

        return FeedProcurementPlanResponse.builder()
                .date(effectiveDate)
                .lookbackDays(forecast.getLookbackDays())
                .horizonDays(safeHorizonDays)
                .totalMaterialsConsidered(forecastItems.size())
                .itemsPlanned(items.size())
                .highUrgencyItems(highUrgency)
                .mediumUrgencyItems(mediumUrgency)
                .lowUrgencyItems(lowUrgency)
                .totalRecommendedQty(roundTo2(totalRecommendedQty))
                .totalEstimatedCost(hasEstimatedCost ? roundTo2(totalEstimatedCost) : null)
                .supplierGroups(supplierGroups)
                .items(items)
                .build();
    }

    public FeedProcurementTaskGenerationResponse generateProcurementTasks(
            LocalDate date,
            Integer lookbackDays,
            Integer horizonDays,
            LocalDate taskDate,
            String actor
    ) {
        return runProcurementTaskGeneration(date, lookbackDays, horizonDays, taskDate, actor, "MANUAL", null);
    }

    public FeedProcurementTaskGenerationResponse generateProcurementTasksAutomated(
            LocalDate date,
            Integer lookbackDays,
            Integer horizonDays,
            LocalDate taskDate,
            String actor,
            String notes
    ) {
        return runProcurementTaskGeneration(date, lookbackDays, horizonDays, taskDate, actor, "AUTOMATED", notes);
    }

    public List<FeedProcurementRunResponse> procurementRuns(Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(50, limit));
        return feedProcurementRunRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(safeLimit)
                .map(this::toProcurementRunResponse)
                .toList();
    }

    private FeedProcurementTaskGenerationResponse runProcurementTaskGeneration(
            LocalDate date,
            Integer lookbackDays,
            Integer horizonDays,
            LocalDate taskDate,
            String actor,
            String runMode,
            String runNotes
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        LocalDate effectiveTaskDate = taskDate != null ? taskDate : effectiveDate;

        FeedProcurementPlanResponse plan = procurementPlan(effectiveDate, lookbackDays, horizonDays);
        List<FeedProcurementPlanItemResponse> actionableItems = plan.getItems().stream()
                .filter(item -> item.getRecommendedOrderQty() > 0)
                .filter(item -> "HIGH".equalsIgnoreCase(item.getUrgencyLevel()) || "MEDIUM".equalsIgnoreCase(item.getUrgencyLevel()))
                .toList();

        List<FeedSopTaskEntity> existingTasks = feedSopTaskRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(effectiveTaskDate);
        Set<String> existingTitles = new HashSet<>();
        for (FeedSopTaskEntity task : existingTasks) {
            String normalizedTitle = trimToNull(task.getTitle());
            if (normalizedTitle != null) {
                existingTitles.add(normalizedTitle.toLowerCase(Locale.ROOT));
            }
        }

        int createdTasks = 0;
        int skippedTasks = 0;
        List<String> createdTaskIds = new ArrayList<>();
        List<String> skippedTaskTitles = new ArrayList<>();

        for (FeedProcurementPlanItemResponse item : actionableItems) {
            String title = procurementTitle(item);
            String titleKey = title.toLowerCase(Locale.ROOT);
            if (existingTitles.contains(titleKey)) {
                skippedTasks++;
                skippedTaskTitles.add(title);
                continue;
            }

            FeedSopTaskPriority priority = "HIGH".equalsIgnoreCase(item.getUrgencyLevel())
                    ? FeedSopTaskPriority.HIGH
                    : FeedSopTaskPriority.MEDIUM;
            LocalTime dueTime = priority == FeedSopTaskPriority.HIGH ? LocalTime.of(10, 0) : LocalTime.of(15, 0);

            FeedSopTaskEntity entity = FeedSopTaskEntity.builder()
                    .feedTaskId(buildId("FT"))
                    .taskDate(effectiveTaskDate)
                    .title(title)
                    .details(procurementDetails(item, plan.getDate(), plan.getHorizonDays()))
                    .assignedRole(UserRole.FEED_MANAGER)
                    .assignedToUsername(null)
                    .assignedByUsername(normalizeActor(actor))
                    .assignedAt(LocalDateTime.now())
                    .priority(priority)
                    .status(FeedSopTaskStatus.PENDING)
                    .dueTime(dueTime)
                    .build();

            FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
            taskAutomationService.upsertFromFeedTask(saved);
            createdTasks++;
            createdTaskIds.add(saved.getFeedTaskId());
            existingTitles.add(titleKey);
        }

        FeedProcurementRunEntity run = feedProcurementRunRepository.save(
                FeedProcurementRunEntity.builder()
                        .feedProcurementRunId(buildId("FPR"))
                        .planDate(effectiveDate)
                        .taskDate(effectiveTaskDate)
                        .runMode(runMode)
                        .lookbackDays(plan.getLookbackDays())
                        .horizonDays(plan.getHorizonDays())
                        .consideredItems(actionableItems.size())
                        .createdTasks(createdTasks)
                        .skippedTasks(skippedTasks)
                        .actor(normalizeActor(actor))
                        .notes(trimToNull(runNotes))
                        .build()
        );

        return FeedProcurementTaskGenerationResponse.builder()
                .feedProcurementRunId(run.getFeedProcurementRunId())
                .runMode(runMode)
                .date(effectiveDate)
                .taskDate(effectiveTaskDate)
                .lookbackDays(plan.getLookbackDays())
                .horizonDays(plan.getHorizonDays())
                .consideredItems(actionableItems.size())
                .createdTasks(createdTasks)
                .skippedTasks(skippedTasks)
                .createdTaskIds(createdTaskIds)
                .skippedTaskTitles(skippedTaskTitles)
                .build();
    }


    public FeedEfficiencyInsightResponse efficiency(LocalDate date, Integer lookbackDays) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        int safeLookbackDays = lookbackDays == null ? 30 : Math.max(14, Math.min(120, lookbackDays));
        LocalDate fromDate = effectiveDate.minusDays(safeLookbackDays - 1L);

        List<AnimalEntity> activeAnimals = animalRepository.findByIsActive(true);
        if (activeAnimals.isEmpty()) {
            return FeedEfficiencyInsightResponse.builder()
                    .date(effectiveDate)
                    .lookbackDays(safeLookbackDays)
                    .fromDate(fromDate)
                    .herdTrend("INSUFFICIENT_DATA")
                    .phaseSummaries(List.of())
                    .items(List.of())
                    .build();
        }

        Map<String, AnimalEntity> animalsById = new HashMap<>();
        for (AnimalEntity animal : activeAnimals) {
            animalsById.put(animal.getAnimalId(), animal);
        }

        List<FeedLogEntity> feedRows = feedLogRepository.findByFeedDateBetweenOrderByFeedDateAscCreatedAtAsc(
                fromDate,
                effectiveDate
        );
        List<MilkEntryEntity> milkRows = milkEntryRepository.findByDateBetween(fromDate, effectiveDate);

        Map<String, Double> totalFeedByAnimal = new HashMap<>();
        Map<String, Double> totalMilkByAnimal = new HashMap<>();
        Map<String, Set<LocalDate>> feedLogDaysByAnimal = new HashMap<>();
        Map<String, Set<LocalDate>> milkLogDaysByAnimal = new HashMap<>();
        Map<String, Map<LocalDate, Double>> feedDailyByAnimal = new HashMap<>();
        Map<String, Map<LocalDate, Double>> milkDailyByAnimal = new HashMap<>();
        Map<String, Map<FeedRationPhase, Integer>> phaseCountsByAnimal = new HashMap<>();

        double totalFeedKg = 0;
        double totalMilkLiters = 0;

        for (FeedLogEntity row : feedRows) {
            String animalId = trimToNull(row.getAnimalId());
            if (animalId == null || !animalsById.containsKey(animalId)) {
                continue;
            }
            double qty = row.getQuantityKg();
            if (qty <= 0) {
                continue;
            }

            totalFeedKg += qty;
            totalFeedByAnimal.merge(animalId, qty, Double::sum);
            feedLogDaysByAnimal.computeIfAbsent(animalId, ignored -> new HashSet<>()).add(row.getFeedDate());
            feedDailyByAnimal.computeIfAbsent(animalId, ignored -> new HashMap<>())
                    .merge(row.getFeedDate(), qty, Double::sum);

            FeedRationPhase phase = row.getRationPhase() != null
                    ? row.getRationPhase()
                    : inferRationPhaseFromAnimal(animalsById.get(animalId));
            phaseCountsByAnimal.computeIfAbsent(animalId, ignored -> new EnumMap<>(FeedRationPhase.class))
                    .merge(phase, 1, Integer::sum);
        }

        for (MilkEntryEntity row : milkRows) {
            String animalId = trimToNull(row.getAnimalId());
            if (animalId == null || !animalsById.containsKey(animalId)) {
                continue;
            }
            double liters = row.getLiters();
            if (liters <= 0) {
                continue;
            }

            totalMilkLiters += liters;
            totalMilkByAnimal.merge(animalId, liters, Double::sum);
            milkLogDaysByAnimal.computeIfAbsent(animalId, ignored -> new HashSet<>()).add(row.getDate());
            milkDailyByAnimal.computeIfAbsent(animalId, ignored -> new HashMap<>())
                    .merge(row.getDate(), liters, Double::sum);
        }

        List<Double> feedPerLiterSamples = new ArrayList<>();
        for (AnimalEntity animal : activeAnimals) {
            double animalFeed = totalFeedByAnimal.getOrDefault(animal.getAnimalId(), 0.0);
            double animalMilk = totalMilkByAnimal.getOrDefault(animal.getAnimalId(), 0.0);
            if (animalFeed > 0 && animalMilk > 0) {
                feedPerLiterSamples.add(animalFeed / animalMilk);
            }
        }

        Double herdMedianFeedPerLiter = median(feedPerLiterSamples);
        Double herdFeedPerLiter = ratio(totalFeedKg, totalMilkLiters);
        Double herdMilkPerKgFeed = ratio(totalMilkLiters, totalFeedKg);

        LocalDate recentFrom = effectiveDate.minusDays(6);
        LocalDate priorFrom = effectiveDate.minusDays(13);
        LocalDate priorTo = effectiveDate.minusDays(7);
        Double recent7HerdFeedPerLiter = ratio(
                sumAcrossAnimals(feedDailyByAnimal, recentFrom, effectiveDate),
                sumAcrossAnimals(milkDailyByAnimal, recentFrom, effectiveDate)
        );
        Double prior7HerdFeedPerLiter = ratio(
                sumAcrossAnimals(feedDailyByAnimal, priorFrom, priorTo),
                sumAcrossAnimals(milkDailyByAnimal, priorFrom, priorTo)
        );
        String herdTrend = detectTrend(recent7HerdFeedPerLiter, prior7HerdFeedPerLiter);

        Map<FeedRationPhase, FeedPhaseAccumulator> phaseAccumulators = new EnumMap<>(FeedRationPhase.class);
        List<FeedEfficiencyAnimalItemResponse> items = new ArrayList<>();
        int efficientAnimals = 0;
        int watchAnimals = 0;
        int inefficientAnimals = 0;
        int dataGapAnimals = 0;
        int animalsWithBothFeedAndMilk = 0;
        double potentialFeedSavingsKg30Days = 0;

        for (AnimalEntity animal : activeAnimals) {
            String animalId = animal.getAnimalId();
            double animalFeedKg = totalFeedByAnimal.getOrDefault(animalId, 0.0);
            double animalMilkLiters = totalMilkByAnimal.getOrDefault(animalId, 0.0);
            int feedLogDays = feedLogDaysByAnimal.getOrDefault(animalId, Set.of()).size();
            int milkLogDays = milkLogDaysByAnimal.getOrDefault(animalId, Set.of()).size();
            double avgFeedPerDay = feedLogDays > 0 ? animalFeedKg / feedLogDays : 0;
            double avgMilkPerDay = milkLogDays > 0 ? animalMilkLiters / milkLogDays : 0;
            Double feedPerLiter = ratio(animalFeedKg, animalMilkLiters);
            Double milkPerKgFeed = ratio(animalMilkLiters, animalFeedKg);
            FeedRationPhase dominantPhase = dominantRationPhase(
                    phaseCountsByAnimal.get(animalId),
                    animal
            );
            Double recent7FeedPerLiter = ratio(
                    sumInRange(feedDailyByAnimal.get(animalId), recentFrom, effectiveDate),
                    sumInRange(milkDailyByAnimal.get(animalId), recentFrom, effectiveDate)
            );
            Double prior7FeedPerLiter = ratio(
                    sumInRange(feedDailyByAnimal.get(animalId), priorFrom, priorTo),
                    sumInRange(milkDailyByAnimal.get(animalId), priorFrom, priorTo)
            );
            String trend = detectTrend(recent7FeedPerLiter, prior7FeedPerLiter);

            EfficiencyDecision decision = classifyAnimalEfficiency(
                    animal,
                    animalFeedKg,
                    animalMilkLiters,
                    feedPerLiter,
                    herdMedianFeedPerLiter,
                    trend
            );

            if ("INEFFICIENT".equals(decision.band)) {
                inefficientAnimals++;
            } else if ("WATCH".equals(decision.band)) {
                watchAnimals++;
            } else if ("DATA_GAP".equals(decision.band)) {
                dataGapAnimals++;
            } else {
                efficientAnimals++;
            }

            if (animalFeedKg > 0 && animalMilkLiters > 0) {
                animalsWithBothFeedAndMilk++;
            }

            if (feedPerLiter != null && herdMedianFeedPerLiter != null && feedPerLiter > herdMedianFeedPerLiter) {
                double expectedFeed = animalMilkLiters * herdMedianFeedPerLiter;
                double excessFeed = Math.max(0, animalFeedKg - expectedFeed);
                potentialFeedSavingsKg30Days += (excessFeed / safeLookbackDays) * 30.0;
            }

            if (animalFeedKg > 0 || animalMilkLiters > 0) {
                FeedPhaseAccumulator acc = phaseAccumulators.computeIfAbsent(
                        dominantPhase,
                        ignored -> new FeedPhaseAccumulator()
                );
                acc.animals += 1;
                acc.totalFeedKg += animalFeedKg;
                acc.totalMilkLiters += animalMilkLiters;
            }

            items.add(
                    FeedEfficiencyAnimalItemResponse.builder()
                            .animalId(animalId)
                            .tag(animal.getTag())
                            .name(animal.getName())
                            .status(animal.getStatus())
                            .growthStage(animal.getGrowthStage())
                            .dominantRationPhase(dominantPhase)
                            .feedLogDays(feedLogDays)
                            .milkLogDays(milkLogDays)
                            .totalFeedKg(roundTo2(animalFeedKg))
                            .totalMilkLiters(roundTo2(animalMilkLiters))
                            .avgFeedPerFeedDayKg(roundTo2(avgFeedPerDay))
                            .avgMilkPerMilkDayLiters(roundTo2(avgMilkPerDay))
                            .feedPerLiter(feedPerLiter)
                            .milkPerKgFeed(milkPerKgFeed)
                            .recent7FeedPerLiter(recent7FeedPerLiter)
                            .prior7FeedPerLiter(prior7FeedPerLiter)
                            .trend(trend)
                            .efficiencyBand(decision.band)
                            .anomalyCode(decision.anomalyCode)
                            .recommendation(decision.recommendation)
                            .build()
            );
        }

        items.sort(
                Comparator.comparingInt((FeedEfficiencyAnimalItemResponse item) -> bandRank(item.getEfficiencyBand()))
                        .thenComparing(
                                (FeedEfficiencyAnimalItemResponse item) ->
                                        item.getFeedPerLiter() == null ? -1 : -item.getFeedPerLiter()
                        )
                        .thenComparing(item -> item.getTag() == null ? "" : item.getTag())
        );

        List<FeedEfficiencyPhaseSummaryResponse> phaseSummaries = new ArrayList<>();
        for (FeedRationPhase phase : FeedRationPhase.values()) {
            FeedPhaseAccumulator acc = phaseAccumulators.get(phase);
            if (acc == null) {
                continue;
            }
            Double phaseFeedPerLiter = ratio(acc.totalFeedKg, acc.totalMilkLiters);
            Double phaseMilkPerKgFeed = ratio(acc.totalMilkLiters, acc.totalFeedKg);
            phaseSummaries.add(
                    FeedEfficiencyPhaseSummaryResponse.builder()
                            .rationPhase(phase)
                            .animals(acc.animals)
                            .totalFeedKg(roundTo2(acc.totalFeedKg))
                            .totalMilkLiters(roundTo2(acc.totalMilkLiters))
                            .feedPerLiter(phaseFeedPerLiter)
                            .milkPerKgFeed(phaseMilkPerKgFeed)
                            .recommendation(phaseRecommendation(phaseFeedPerLiter, herdMedianFeedPerLiter))
                            .build()
            );
        }

        Double avgFeedCostPerKg = averageFeedCostPerKg();
        double potentialFeedCostSavings30Days = avgFeedCostPerKg == null
                ? 0
                : potentialFeedSavingsKg30Days * avgFeedCostPerKg;

        return FeedEfficiencyInsightResponse.builder()
                .date(effectiveDate)
                .lookbackDays(safeLookbackDays)
                .fromDate(fromDate)
                .animalsTracked(activeAnimals.size())
                .animalsWithBothFeedAndMilk(animalsWithBothFeedAndMilk)
                .efficientAnimals(efficientAnimals)
                .watchAnimals(watchAnimals)
                .inefficientAnimals(inefficientAnimals)
                .dataGapAnimals(dataGapAnimals)
                .totalFeedKg(roundTo2(totalFeedKg))
                .totalMilkLiters(roundTo2(totalMilkLiters))
                .herdFeedPerLiter(herdFeedPerLiter)
                .herdMilkPerKgFeed(herdMilkPerKgFeed)
                .herdMedianFeedPerLiter(herdMedianFeedPerLiter == null ? null : roundTo3(herdMedianFeedPerLiter))
                .recent7HerdFeedPerLiter(recent7HerdFeedPerLiter)
                .prior7HerdFeedPerLiter(prior7HerdFeedPerLiter)
                .herdTrend(herdTrend)
                .avgFeedCostPerKg(avgFeedCostPerKg == null ? null : roundTo2(avgFeedCostPerKg))
                .potentialFeedSavingsKg30Days(roundTo2(potentialFeedSavingsKg30Days))
                .potentialFeedCostSavings30Days(roundTo2(potentialFeedCostSavings30Days))
                .phaseSummaries(phaseSummaries)
                .items(items)
                .build();
    }

    public FeedMaterialResponse createMaterial(CreateFeedMaterialRequest req) {
        String name = normalizeRequired(req.getMaterialName(), "Material name is required");
        feedMaterialRepository.findByMaterialNameIgnoreCase(name)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Material with same name already exists");
                });

        FeedMaterialEntity entity = FeedMaterialEntity.builder()
                .feedMaterialId(buildId("FM"))
                .materialName(name)
                .category(req.getCategory())
                .unit(req.getUnit())
                .availableQty(req.getAvailableQty())
                .reorderLevelQty(req.getReorderLevelQty())
                .costPerUnit(req.getCostPerUnit())
                .supplierName(trimToNull(req.getSupplierName()))
                .notes(trimToNull(req.getNotes()))
                .build();
        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public FeedMaterialResponse updateMaterial(String materialId, UpdateFeedMaterialRequest req) {
        FeedMaterialEntity entity = feedMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        String name = normalizeRequired(req.getMaterialName(), "Material name is required");
        feedMaterialRepository.findByMaterialNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getFeedMaterialId().equals(materialId)) {
                throw new IllegalArgumentException("Material with same name already exists");
            }
        });

        entity.setMaterialName(name);
        entity.setCategory(req.getCategory());
        entity.setUnit(req.getUnit());
        entity.setAvailableQty(req.getAvailableQty());
        entity.setReorderLevelQty(req.getReorderLevelQty());
        entity.setCostPerUnit(req.getCostPerUnit());
        entity.setSupplierName(trimToNull(req.getSupplierName()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public FeedMaterialResponse adjustMaterialStock(String materialId, AdjustFeedStockRequest req, String actor) {
        FeedMaterialEntity entity = feedMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        double delta = req.getQuantityDelta();
        if (delta == 0) {
            throw new IllegalArgumentException("Stock delta cannot be 0");
        }

        double next = entity.getAvailableQty() + delta;
        if (next < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }

        entity.setAvailableQty(next);
        String reason = trimToNull(req.getReason());
        if (reason != null) {
            String audit = String.format(Locale.ENGLISH, "[%s] %s (%+.2f)", actor, reason, delta);
            entity.setNotes(appendNote(entity.getNotes(), audit));
        }

        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public List<FeedRecipeResponse> listRecipes(Boolean activeOnly, FeedRationPhase rationPhase) {
        List<FeedRecipeEntity> rows;
        if (rationPhase != null) {
            rows = feedRecipeRepository.findByRationPhaseOrderByUpdatedAtDesc(rationPhase);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            rows = feedRecipeRepository.findByActiveTrueOrderByUpdatedAtDesc();
        } else {
            rows = feedRecipeRepository.findAllByOrderByUpdatedAtDesc();
        }
        if (Boolean.TRUE.equals(activeOnly) && rationPhase != null) {
            rows = rows.stream().filter(FeedRecipeEntity::isActive).toList();
        }
        return rows.stream().map(this::toRecipeResponse).toList();
    }

    public FeedRecipeResponse createRecipe(CreateFeedRecipeRequest req) {
        FeedRecipeEntity entity = FeedRecipeEntity.builder()
                .feedRecipeId(buildId("FR"))
                .recipeName(normalizeRequired(req.getRecipeName(), "Recipe name is required"))
                .rationPhase(req.getRationPhase())
                .targetAnimalCount(req.getTargetAnimalCount())
                .ingredients(normalizeRequired(req.getIngredients(), "Ingredients are required"))
                .instructions(trimToNull(req.getInstructions()))
                .active(req.getActive() == null || req.getActive())
                .build();
        return toRecipeResponse(feedRecipeRepository.save(entity));
    }

    public FeedRecipeResponse updateRecipe(String recipeId, UpdateFeedRecipeRequest req) {
        FeedRecipeEntity entity = feedRecipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        entity.setRecipeName(normalizeRequired(req.getRecipeName(), "Recipe name is required"));
        entity.setRationPhase(req.getRationPhase());
        entity.setTargetAnimalCount(req.getTargetAnimalCount());
        entity.setIngredients(normalizeRequired(req.getIngredients(), "Ingredients are required"));
        entity.setInstructions(trimToNull(req.getInstructions()));
        entity.setActive(req.getActive());
        return toRecipeResponse(feedRecipeRepository.save(entity));
    }

    public List<FeedSopTaskResponse> listTasks(
            LocalDate date,
            FeedSopTaskStatus status,
            UserRole assignedRole,
            String assignedToUsername,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        List<FeedSopTaskEntity> rows = findTasks(date, status, assignedRole);
        String normalizedActor = normalizeActor(actorUsername);
        String normalizedAssignedTo = trimToNull(assignedToUsername);

        if (normalizedAssignedTo != null) {
            if (!privilegedActor && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("Cannot view tasks assigned to another user");
            }
            rows = rows.stream()
                    .filter(row -> normalizedAssignedTo.equalsIgnoreCase(trimToNull(row.getAssignedToUsername())))
                    .toList();
        }

        if (!privilegedActor) {
            UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
            rows = rows.stream()
                    .filter(row -> {
                        String assignee = trimToNull(row.getAssignedToUsername());
                        if (assignee != null) {
                            return assignee.equalsIgnoreCase(normalizedActor);
                        }
                        return row.getAssignedRole() == safeActorRole;
                    })
                    .toList();
        }

        return rows.stream().map(this::toTaskResponse).toList();
    }

    public FeedSopTaskResponse createTask(CreateFeedSopTaskRequest req, String actor) {
        UserRole finalAssignedRole = req.getAssignedRole() != null ? req.getAssignedRole() : UserRole.WORKER;
        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), finalAssignedRole);
        FeedSopTaskEntity entity = FeedSopTaskEntity.builder()
                .feedTaskId(buildId("FT"))
                .taskDate(req.getTaskDate())
                .title(normalizeRequired(req.getTitle(), "Task title is required"))
                .details(trimToNull(req.getDetails()))
                .assignedRole(finalAssignedRole)
                .assignedToUsername(normalizedAssignedTo)
                .assignedByUsername(normalizedAssignedTo != null ? normalizeActor(actor) : null)
                .assignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null)
                .priority(req.getPriority() != null ? req.getPriority() : FeedSopTaskPriority.MEDIUM)
                .status(FeedSopTaskStatus.PENDING)
                .dueTime(req.getDueTime())
                .build();
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    public FeedSopTaskResponse updateTask(String taskId, UpdateFeedSopTaskRequest req, String actor) {
        FeedSopTaskEntity entity = feedSopTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), req.getAssignedRole());
        String previousAssignee = trimToNull(entity.getAssignedToUsername());

        entity.setTaskDate(req.getTaskDate());
        entity.setTitle(normalizeRequired(req.getTitle(), "Task title is required"));
        entity.setDetails(trimToNull(req.getDetails()));
        entity.setAssignedRole(req.getAssignedRole());
        entity.setAssignedToUsername(normalizedAssignedTo);
        if (!sameUsername(previousAssignee, normalizedAssignedTo)) {
            entity.setAssignedByUsername(normalizedAssignedTo != null ? normalizeActor(actor) : null);
            entity.setAssignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null);
        }
        entity.setPriority(req.getPriority());
        entity.setStatus(req.getStatus());
        entity.setDueTime(req.getDueTime());
        applyCompletionMetadata(entity, req.getStatus(), actor);
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    public FeedSopTaskResponse updateTaskStatus(
            String taskId,
            UpdateFeedSopTaskStatusRequest req,
            String actor,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        FeedSopTaskEntity entity = feedSopTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        String normalizedActor = normalizeActor(actor);
        String assignedTo = trimToNull(entity.getAssignedToUsername());
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;

        if (!privilegedActor) {
            if (assignedTo != null && !assignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("Task is assigned to another user");
            }
            if (assignedTo == null) {
                if (entity.getAssignedRole() != safeActorRole) {
                    throw new IllegalArgumentException("Task role does not match your role");
                }
                entity.setAssignedToUsername(normalizedActor);
                entity.setAssignedByUsername("system");
                entity.setAssignedAt(LocalDateTime.now());
            }
        }

        entity.setStatus(req.getStatus());
        applyCompletionMetadata(entity, req.getStatus(), actor);
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    private void applyCompletionMetadata(FeedSopTaskEntity entity, FeedSopTaskStatus status, String actor) {
        if (status == FeedSopTaskStatus.DONE) {
            entity.setCompletedAt(LocalDateTime.now());
            entity.setCompletedBy(actor);
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }
    }

    private FeedMaterialResponse toMaterialResponse(FeedMaterialEntity entity) {
        return FeedMaterialResponse.builder()
                .feedMaterialId(entity.getFeedMaterialId())
                .materialName(entity.getMaterialName())
                .category(entity.getCategory())
                .unit(entity.getUnit())
                .availableQty(entity.getAvailableQty())
                .reorderLevelQty(entity.getReorderLevelQty())
                .costPerUnit(entity.getCostPerUnit())
                .supplierName(entity.getSupplierName())
                .notes(entity.getNotes())
                .lowStock(isLowStock(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FeedRecipeResponse toRecipeResponse(FeedRecipeEntity entity) {
        return FeedRecipeResponse.builder()
                .feedRecipeId(entity.getFeedRecipeId())
                .recipeName(entity.getRecipeName())
                .rationPhase(entity.getRationPhase())
                .targetAnimalCount(entity.getTargetAnimalCount())
                .ingredients(entity.getIngredients())
                .instructions(entity.getInstructions())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FeedSopTaskResponse toTaskResponse(FeedSopTaskEntity entity) {
        return FeedSopTaskResponse.builder()
                .feedTaskId(entity.getFeedTaskId())
                .taskDate(entity.getTaskDate())
                .title(entity.getTitle())
                .details(entity.getDetails())
                .assignedRole(entity.getAssignedRole())
                .assignedToUsername(entity.getAssignedToUsername())
                .assignedByUsername(entity.getAssignedByUsername())
                .assignedAt(entity.getAssignedAt())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .dueTime(entity.getDueTime())
                .completedAt(entity.getCompletedAt())
                .completedBy(entity.getCompletedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private boolean isLowStock(FeedMaterialEntity entity) {
        return entity.getAvailableQty() <= entity.getReorderLevelQty();
    }

    private FeedMaterialCategory mapFeedTypeToCategory(String feedType) {
        String normalized = trimToNull(feedType);
        if (normalized == null) {
            return FeedMaterialCategory.OTHER;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("green")) {
            return FeedMaterialCategory.GREEN_FODDER;
        }
        if (lower.contains("dry")) {
            return FeedMaterialCategory.DRY_FODDER;
        }
        if (lower.contains("concentrate") || lower.contains("cattle feed")) {
            return FeedMaterialCategory.CONCENTRATE;
        }
        if (lower.contains("mineral")) {
            return FeedMaterialCategory.MINERAL;
        }
        if (lower.contains("additive")) {
            return FeedMaterialCategory.ADDITIVE;
        }
        return FeedMaterialCategory.OTHER;
    }

    private FeedRationPhase inferRationPhaseFromAnimal(AnimalEntity animal) {
        if (animal == null) {
            return FeedRationPhase.DRY;
        }
        if (animal.getGrowthStage() == AnimalGrowthStage.CALF) {
            return FeedRationPhase.CALF;
        }
        if (animal.getStatus() == AnimalStatus.LACTATING) {
            return FeedRationPhase.LACTATING;
        }
        if (animal.getStatus() == AnimalStatus.SICK) {
            return FeedRationPhase.SICK_RECOVERY;
        }
        return FeedRationPhase.DRY;
    }

    private FeedRationPhase dominantRationPhase(Map<FeedRationPhase, Integer> counts, AnimalEntity animal) {
        if (counts == null || counts.isEmpty()) {
            return inferRationPhaseFromAnimal(animal);
        }
        FeedRationPhase selected = null;
        int best = -1;
        for (Map.Entry<FeedRationPhase, Integer> row : counts.entrySet()) {
            if (row.getValue() != null && row.getValue() > best) {
                selected = row.getKey();
                best = row.getValue();
            }
        }
        return selected == null ? inferRationPhaseFromAnimal(animal) : selected;
    }

    private double sumAcrossAnimals(
            Map<String, Map<LocalDate, Double>> byAnimal,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        double sum = 0;
        for (Map<LocalDate, Double> perDay : byAnimal.values()) {
            sum += sumInRange(perDay, fromDate, toDate);
        }
        return sum;
    }

    private double sumInRange(Map<LocalDate, Double> perDay, LocalDate fromDate, LocalDate toDate) {
        if (perDay == null || perDay.isEmpty() || fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            return 0;
        }
        double sum = 0;
        for (Map.Entry<LocalDate, Double> row : perDay.entrySet()) {
            LocalDate d = row.getKey();
            if (d != null && (d.isEqual(fromDate) || d.isAfter(fromDate)) && (d.isEqual(toDate) || d.isBefore(toDate))) {
                sum += row.getValue() == null ? 0 : row.getValue();
            }
        }
        return sum;
    }

    private Double ratio(double numerator, double denominator) {
        if (denominator <= 0) {
            return null;
        }
        return roundTo3(numerator / denominator);
    }

    private String detectTrend(Double recentRatio, Double priorRatio) {
        if (recentRatio == null || priorRatio == null) {
            return "INSUFFICIENT_DATA";
        }
        if (recentRatio <= priorRatio * 0.90) {
            return "IMPROVING";
        }
        if (recentRatio >= priorRatio * 1.10) {
            return "DECLINING";
        }
        return "STABLE";
    }

    private Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().filter(v -> v != null && v > 0).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private Double averageFeedCostPerKg() {
        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        double total = 0;
        int count = 0;
        for (FeedMaterialEntity row : materials) {
            if (row.getUnit() != FeedMaterialUnit.KG || row.getCostPerUnit() == null || row.getCostPerUnit() <= 0) {
                continue;
            }
            total += row.getCostPerUnit();
            count++;
        }
        if (count == 0) {
            return null;
        }
        return total / count;
    }

    private EfficiencyDecision classifyAnimalEfficiency(
            AnimalEntity animal,
            double totalFeedKg,
            double totalMilkLiters,
            Double feedPerLiter,
            Double herdMedianFeedPerLiter,
            String trend
    ) {
        if (totalFeedKg <= 0 && totalMilkLiters <= 0) {
            return new EfficiencyDecision(
                    "DATA_GAP",
                    "NO_DATA",
                    "No feed or milk logs in selected window. Log both consistently for optimization."
            );
        }
        if (totalFeedKg <= 0 && totalMilkLiters > 0) {
            return new EfficiencyDecision(
                    "DATA_GAP",
                    "MISSING_FEED_LOGS",
                    "Milk output exists but feed logs are missing. Improve feed logging discipline."
            );
        }
        if (totalFeedKg > 0 && totalMilkLiters <= 0) {
            String noMilkRecommendation = animal != null && animal.getStatus() == AnimalStatus.LACTATING
                    ? "Feed is logged but milk output is missing. Check health and milk-entry process urgently."
                    : "Feed is logged without milk output. Verify lifecycle status and milk-entry capture.";
            return new EfficiencyDecision("INEFFICIENT", "NO_MILK_WITH_FEED", noMilkRecommendation);
        }
        if (feedPerLiter == null) {
            return new EfficiencyDecision(
                    "WATCH",
                    "RATIO_UNAVAILABLE",
                    "Feed-to-milk ratio could not be derived. Review log quality."
            );
        }

        double watchThreshold = herdMedianFeedPerLiter == null ? 1.50 : herdMedianFeedPerLiter * 1.15;
        double highThreshold = herdMedianFeedPerLiter == null ? 1.80 : herdMedianFeedPerLiter * 1.35;

        if (feedPerLiter >= highThreshold) {
            return new EfficiencyDecision(
                    "INEFFICIENT",
                    "HIGH_FEED_PER_LITER",
                    String.format(
                            Locale.ENGLISH,
                            "Feed per liter (%.3f) is well above herd baseline. Review ration quality, timings, and health checks.",
                            feedPerLiter
                    )
            );
        }
        if (feedPerLiter >= watchThreshold) {
            return new EfficiencyDecision(
                    "WATCH",
                    "ABOVE_BASELINE",
                    String.format(
                            Locale.ENGLISH,
                            "Feed per liter (%.3f) is above herd baseline. Monitor for 7-14 days and tune concentrate mix.",
                            feedPerLiter
                    )
            );
        }
        if ("DECLINING".equals(trend)) {
            return new EfficiencyDecision(
                    "WATCH",
                    "DECLINING_TREND",
                    "Recent trend is declining. Check ration consistency and health follow-ups."
            );
        }
        return new EfficiencyDecision(
                "EFFICIENT",
                "WITHIN_BASELINE",
                "Within herd baseline efficiency. Continue current ration and consistency."
        );
    }

    private String phaseRecommendation(Double phaseFeedPerLiter, Double herdMedianFeedPerLiter) {
        if (phaseFeedPerLiter == null) {
            return "Insufficient feed/milk overlap data for this phase.";
        }
        if (herdMedianFeedPerLiter == null) {
            return "Phase baseline is available; collect more data to compare against herd median.";
        }
        if (phaseFeedPerLiter > herdMedianFeedPerLiter * 1.2) {
            return "Above herd baseline. Recheck ration density, schedule, and animal-health follow-up.";
        }
        if (phaseFeedPerLiter < herdMedianFeedPerLiter * 0.9) {
            return "Better than herd baseline. Keep this phase strategy stable.";
        }
        return "Close to herd baseline. Continue monitoring and incremental tuning.";
    }

    private int bandRank(String band) {
        if ("INEFFICIENT".equalsIgnoreCase(band)) {
            return 0;
        }
        if ("WATCH".equalsIgnoreCase(band)) {
            return 1;
        }
        if ("DATA_GAP".equalsIgnoreCase(band)) {
            return 2;
        }
        return 3;
    }

    private int procurementUrgencyScore(FeedInventoryForecastItemResponse row, double recommendedQty) {
        int score;
        if ("HIGH".equalsIgnoreCase(row.getRiskLevel())) {
            score = 70;
        } else if ("MEDIUM".equalsIgnoreCase(row.getRiskLevel())) {
            score = 45;
        } else {
            score = 20;
        }

        if (row.isLowStock()) {
            score += 15;
        }

        Double daysLeft = row.getDaysOfStockLeft();
        if (daysLeft != null) {
            if (daysLeft <= 7) {
                score += 20;
            } else if (daysLeft <= 15) {
                score += 12;
            } else if (daysLeft <= 30) {
                score += 6;
            }
        }

        if (recommendedQty >= 300) {
            score += 8;
        } else if (recommendedQty >= 100) {
            score += 5;
        } else if (recommendedQty >= 25) {
            score += 2;
        }

        return Math.max(0, Math.min(100, score));
    }

    private String procurementUrgencyLevel(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LocalDate suggestedOrderByDate(LocalDate today, String urgencyLevel, Double daysLeft) {
        if ("HIGH".equalsIgnoreCase(urgencyLevel)) {
            return today;
        }
        if ("MEDIUM".equalsIgnoreCase(urgencyLevel)) {
            return today.plusDays(2);
        }
        if (daysLeft != null && daysLeft <= 45) {
            return today.plusDays(4);
        }
        return today.plusDays(7);
    }

    private String procurementTitle(FeedProcurementPlanItemResponse item) {
        return "Procure " + item.getMaterialName();
    }

    private String procurementDetails(FeedProcurementPlanItemResponse item, LocalDate planDate, int horizonDays) {
        String supplier = trimToNull(item.getSupplierName());
        String unitLabel = item.getUnit() == null ? "UNIT" : item.getUnit().name();
        String orderBy = item.getSuggestedOrderByDate() == null ? "ASAP" : item.getSuggestedOrderByDate().toString();
        String estimatedCost = item.getEstimatedOrderCost() == null
                ? "NA"
                : String.format(Locale.ENGLISH, "Rs %.2f", item.getEstimatedOrderCost());

        return String.format(
                Locale.ENGLISH,
                "Plan date: %s | Horizon: %d days | Supplier: %s | Qty: %.2f %s | Est cost: %s | Risk: %s | Urgency: %s (%d) | Order by: %s | Basis: %s | Note: %s",
                planDate,
                horizonDays,
                supplier == null ? "UNASSIGNED" : supplier,
                item.getRecommendedOrderQty(),
                unitLabel,
                estimatedCost,
                item.getRiskLevel(),
                item.getUrgencyLevel(),
                item.getUrgencyScore(),
                orderBy,
                item.getForecastBasis(),
                item.getRecommendation()
        );
    }

    private FeedProcurementRunResponse toProcurementRunResponse(FeedProcurementRunEntity row) {
        return FeedProcurementRunResponse.builder()
                .feedProcurementRunId(row.getFeedProcurementRunId())
                .planDate(row.getPlanDate())
                .taskDate(row.getTaskDate())
                .runMode(row.getRunMode())
                .lookbackDays(row.getLookbackDays())
                .horizonDays(row.getHorizonDays())
                .consideredItems(row.getConsideredItems())
                .createdTasks(row.getCreatedTasks())
                .skippedTasks(row.getSkippedTasks())
                .actor(row.getActor())
                .notes(row.getNotes())
                .createdAt(row.getCreatedAt())
                .build();
    }


    private double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundTo3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<FeedSopTaskEntity> findTasks(LocalDate date, FeedSopTaskStatus status, UserRole assignedRole) {
        if (date != null && status != null && assignedRole != null) {
            return feedSopTaskRepository.findByTaskDateAndStatusAndAssignedRoleOrderByPriorityDescCreatedAtAsc(
                    date,
                    status,
                    assignedRole
            );
        }
        if (date != null && status != null) {
            return feedSopTaskRepository.findByTaskDateAndStatusOrderByPriorityDescCreatedAtAsc(date, status);
        }
        if (date != null && assignedRole != null) {
            return feedSopTaskRepository.findByTaskDateAndAssignedRoleOrderByPriorityDescCreatedAtAsc(date, assignedRole);
        }
        if (date != null) {
            return feedSopTaskRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(date);
        }
        if (status != null && assignedRole != null) {
            return feedSopTaskRepository.findByStatusAndAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(status, assignedRole);
        }
        if (status != null) {
            return feedSopTaskRepository.findByStatusOrderByTaskDateAscPriorityDescCreatedAtAsc(status);
        }
        if (assignedRole != null) {
            return feedSopTaskRepository.findByAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(assignedRole);
        }
        return feedSopTaskRepository.findAllByOrderByTaskDateAscCreatedAtAsc();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String resolveAssignee(String assignedToUsername, UserRole assignedRole) {
        String normalizedAssignedTo = trimToNull(assignedToUsername);
        if (normalizedAssignedTo == null) {
            return null;
        }
        AuthUserEntity assignee = authUserRepository.findByUsernameIgnoreCase(normalizedAssignedTo)
                .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        if (!assignee.isActive()) {
            throw new IllegalArgumentException("Assigned user is inactive");
        }
        if (assignedRole != assignee.getRole()) {
            throw new IllegalArgumentException("Assigned user role must match assignedRole");
        }
        return assignee.getUsername();
    }

    private boolean sameUsername(String left, String right) {
        String a = trimToNull(left);
        String b = trimToNull(right);
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private String appendNote(String current, String line) {
        if (current == null || current.isBlank()) {
            return line;
        }
        if (line == null || line.isBlank()) {
            return current;
        }
        String appended = current + "\n" + line;
        if (appended.length() > 500) {
            return appended.substring(appended.length() - 500);
        }
        return appended;
    }

    private static class FeedPhaseAccumulator {
        private int animals;
        private double totalFeedKg;
        private double totalMilkLiters;
    }

    private record EfficiencyDecision(String band, String anomalyCode, String recommendation) {
    }
}
