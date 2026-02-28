package net.nani.dairy.stock;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.feed.FeedMaterialEntity;
import net.nani.dairy.feed.FeedMaterialRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.sales.ProductType;
import net.nani.dairy.sales.SaleEntity;
import net.nani.dairy.sales.SaleRepository;
import net.nani.dairy.tasks.TaskAutomationService;
import net.nani.dairy.tasks.TaskPriority;
import net.nani.dairy.stock.dto.AdjustProcessingStockRequest;
import net.nani.dairy.stock.dto.CreateProcessingConversionRequest;
import net.nani.dairy.stock.dto.ProcessingStockSummaryResponse;
import net.nani.dairy.stock.dto.ProcessingStockTxnResponse;
import net.nani.dairy.stock.dto.SyncProcessingDayRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessingStockService {

    private static final List<ProcessingStockTxnType> AUTO_TXN_TYPES = List.of(
            ProcessingStockTxnType.AUTO_MILK_PRODUCTION,
            ProcessingStockTxnType.AUTO_SALE_DEDUCTION,
            ProcessingStockTxnType.AUTO_EOD_MILK_TO_CURD
    );

    private final ProcessingStockTxnRepository processingStockTxnRepository;
    private final FeedMaterialRepository feedMaterialRepository;
    private final MilkBatchRepository milkBatchRepository;
    private final SaleRepository saleRepository;
    private final TaskAutomationService taskAutomationService;

    public ProcessingStockSummaryResponse summary(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        Map<ProcessingStockStage, Double> balances = computeBalances(effectiveDate);
        List<SaleEntity> sales = saleRepository.findByDispatchDate(effectiveDate);
        double milkProducedToday = milkBatchRepository.findByDate(effectiveDate)
                .stream()
                .mapToDouble(batch -> batch.getTotalLiters())
                .sum();
        Map<ProductType, Double> soldByProduct = soldByProduct(sales);

        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        int lowStockMaterials = 0;
        double rawMaterialStockValue = 0;
        for (FeedMaterialEntity material : materials) {
            if (material.getAvailableQty() <= material.getReorderLevelQty()) {
                lowStockMaterials++;
            }
            if (material.getCostPerUnit() != null && material.getCostPerUnit() > 0) {
                rawMaterialStockValue += material.getAvailableQty() * material.getCostPerUnit();
            }
        }

        double milkSoldToday = soldByProduct.getOrDefault(ProductType.MILK, 0.0);
        double suggestedEodMilkToCurd = Math.max(0, milkProducedToday - milkSoldToday);
        int transactionsToday = processingStockTxnRepository.findByTxnDateOrderByCreatedAtDesc(effectiveDate).size();

        return ProcessingStockSummaryResponse.builder()
                .date(effectiveDate)
                .rawMaterialItems(materials.size())
                .lowStockRawMaterials(lowStockMaterials)
                .rawMaterialStockValue(rawMaterialStockValue)
                .milkBalanceLiters(balances.getOrDefault(ProcessingStockStage.MILK, 0.0))
                .curdBalanceKg(balances.getOrDefault(ProcessingStockStage.CURD, 0.0))
                .buttermilkBalanceLiters(balances.getOrDefault(ProcessingStockStage.BUTTERMILK, 0.0))
                .gheeBalanceKg(balances.getOrDefault(ProcessingStockStage.GHEE, 0.0))
                .milkProducedToday(milkProducedToday)
                .milkSoldToday(milkSoldToday)
                .curdSoldToday(soldByProduct.getOrDefault(ProductType.CURD, 0.0))
                .buttermilkSoldToday(soldByProduct.getOrDefault(ProductType.BUTTERMILK, 0.0))
                .gheeSoldToday(soldByProduct.getOrDefault(ProductType.GHEE, 0.0))
                .suggestedEodMilkToCurd(suggestedEodMilkToCurd)
                .transactionsToday(transactionsToday)
                .build();
    }

    public List<ProcessingStockTxnResponse> transactions(LocalDate date) {
        List<ProcessingStockTxnEntity> rows = date != null
                ? processingStockTxnRepository.findByTxnDateOrderByCreatedAtDesc(date)
                : processingStockTxnRepository.findAllByOrderByTxnDateDescCreatedAtDesc();
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProcessingStockSummaryResponse syncDay(SyncProcessingDayRequest req, String actorUsername) {
        LocalDate effectiveDate = req != null && req.getDate() != null ? req.getDate() : LocalDate.now();
        boolean autoTransferMilkToCurd = req == null
                || req.getAutoTransferMilkToCurd() == null
                || req.getAutoTransferMilkToCurd();

        processingStockTxnRepository.deleteByTxnDateAndTxnTypeIn(effectiveDate, AUTO_TXN_TYPES);

        double producedMilk = milkBatchRepository.findByDate(effectiveDate)
                .stream()
                .mapToDouble(batch -> batch.getTotalLiters())
                .sum();
        if (producedMilk > 0) {
            saveTxn(
                    effectiveDate,
                    ProcessingStockTxnType.AUTO_MILK_PRODUCTION,
                    "AUTO_MILK_PRODUCTION:" + effectiveDate,
                    null,
                    null,
                    ProcessingStockStage.MILK,
                    producedMilk,
                    "Daily milk production synced from milk batches",
                    actorUsername
            );
        }

        List<SaleEntity> sales = saleRepository.findByDispatchDate(effectiveDate);
        double milkSold = 0;
        for (SaleEntity sale : sales) {
            ProcessingStockStage stage = stageForProduct(sale.getProductType());
            if (stage == null || sale.getQuantity() <= 0) {
                continue;
            }
            if (stage == ProcessingStockStage.MILK) {
                milkSold += sale.getQuantity();
            }
            saveTxn(
                    effectiveDate,
                    ProcessingStockTxnType.AUTO_SALE_DEDUCTION,
                    "AUTO_SALE:" + sale.getSaleId(),
                    stage,
                    sale.getQuantity(),
                    null,
                    null,
                    "Sale deduction synced for " + sale.getProductType(),
                    actorUsername
            );
        }

        double unsoldMilk = Math.max(0, producedMilk - milkSold);

        if (autoTransferMilkToCurd) {
            double transferQty = unsoldMilk;
            if (transferQty > 0) {
                saveTxn(
                        effectiveDate,
                        ProcessingStockTxnType.AUTO_EOD_MILK_TO_CURD,
                        "AUTO_EOD_MILK_TO_CURD:" + effectiveDate,
                        ProcessingStockStage.MILK,
                        transferQty,
                        ProcessingStockStage.CURD,
                        transferQty,
                        "EOD transfer of unsold milk to curd processing",
                        actorUsername
                    );
            }
        }

        upsertPipelineTasks(effectiveDate, unsoldMilk);

        return summary(effectiveDate);
    }

    @Transactional
    public ProcessingStockTxnResponse addConversion(CreateProcessingConversionRequest req, String actorUsername) {
        if (req.getFromStage() == req.getToStage()) {
            throw new IllegalArgumentException("fromStage and toStage cannot be same");
        }
        LocalDate effectiveDate = req.getDate() != null ? req.getDate() : LocalDate.now();
        double inputQty = req.getInputQty();
        double outputQty = req.getOutputQty();
        if (inputQty <= 0 || outputQty <= 0) {
            throw new IllegalArgumentException("inputQty and outputQty must be positive");
        }

        Map<ProcessingStockStage, Double> balances = computeBalances(effectiveDate);
        double availableQty = balances.getOrDefault(req.getFromStage(), 0.0);
        if (availableQty + 1e-9 < inputQty) {
            throw new IllegalArgumentException("Insufficient stock in source stage");
        }

        ProcessingStockTxnEntity saved = saveTxn(
                effectiveDate,
                ProcessingStockTxnType.MANUAL_CONVERSION,
                null,
                req.getFromStage(),
                inputQty,
                req.getToStage(),
                outputQty,
                trimToNull(req.getNotes()),
                actorUsername
        );
        return toResponse(saved);
    }

    @Transactional
    public ProcessingStockTxnResponse adjustStock(AdjustProcessingStockRequest req, String actorUsername) {
        LocalDate effectiveDate = req.getDate() != null ? req.getDate() : LocalDate.now();
        double delta = req.getQuantityDelta();
        if (delta == 0) {
            throw new IllegalArgumentException("quantityDelta cannot be zero");
        }

        if (delta < 0) {
            Map<ProcessingStockStage, Double> balances = computeBalances(effectiveDate);
            double availableQty = balances.getOrDefault(req.getStage(), 0.0);
            if (availableQty + 1e-9 < Math.abs(delta)) {
                throw new IllegalArgumentException("Insufficient stock for deduction");
            }
        }

        ProcessingStockTxnEntity saved;
        if (delta > 0) {
            saved = saveTxn(
                    effectiveDate,
                    ProcessingStockTxnType.MANUAL_ADJUSTMENT,
                    null,
                    null,
                    null,
                    req.getStage(),
                    delta,
                    trimToNull(req.getNotes()),
                    actorUsername
            );
        } else {
            saved = saveTxn(
                    effectiveDate,
                    ProcessingStockTxnType.MANUAL_ADJUSTMENT,
                    null,
                    req.getStage(),
                    Math.abs(delta),
                    null,
                    null,
                    trimToNull(req.getNotes()),
                    actorUsername
            );
        }
        return toResponse(saved);
    }

    private ProcessingStockTxnEntity saveTxn(
            LocalDate date,
            ProcessingStockTxnType type,
            String sourceKey,
            ProcessingStockStage fromStage,
            Double inputQty,
            ProcessingStockStage toStage,
            Double outputQty,
            String notes,
            String actorUsername
    ) {
        ProcessingStockTxnEntity entity = ProcessingStockTxnEntity.builder()
                .stockTxnId("STK_" + UUID.randomUUID().toString().substring(0, 8))
                .txnDate(date)
                .txnType(type)
                .sourceKey(trimToNull(sourceKey))
                .fromStage(fromStage)
                .inputQty(inputQty)
                .toStage(toStage)
                .outputQty(outputQty)
                .notes(trimToNull(notes))
                .actorUsername(trimToNull(actorUsername))
                .build();
        return processingStockTxnRepository.save(entity);
    }

    private Map<ProcessingStockStage, Double> computeBalances(LocalDate date) {
        Map<ProcessingStockStage, Double> balances = new EnumMap<>(ProcessingStockStage.class);
        for (ProcessingStockStage stage : ProcessingStockStage.values()) {
            balances.put(stage, 0.0);
        }
        List<ProcessingStockTxnEntity> rows = processingStockTxnRepository
                .findByTxnDateLessThanEqualOrderByTxnDateAscCreatedAtAsc(date);
        for (ProcessingStockTxnEntity row : rows) {
            if (row.getFromStage() != null) {
                balances.put(
                        row.getFromStage(),
                        balances.getOrDefault(row.getFromStage(), 0.0) - Math.max(0.0, nullToZero(row.getInputQty()))
                );
            }
            if (row.getToStage() != null) {
                balances.put(
                        row.getToStage(),
                        balances.getOrDefault(row.getToStage(), 0.0) + Math.max(0.0, nullToZero(row.getOutputQty()))
                );
            }
        }
        return balances;
    }

    private Map<ProductType, Double> soldByProduct(List<SaleEntity> rows) {
        Map<ProductType, Double> sold = new EnumMap<>(ProductType.class);
        for (SaleEntity row : rows) {
            sold.put(
                    row.getProductType(),
                    sold.getOrDefault(row.getProductType(), 0.0) + Math.max(0.0, row.getQuantity())
            );
        }
        return sold;
    }

    private ProcessingStockStage stageForProduct(ProductType productType) {
        if (productType == null) {
            return null;
        }
        return switch (productType) {
            case MILK -> ProcessingStockStage.MILK;
            case CURD -> ProcessingStockStage.CURD;
            case BUTTERMILK -> ProcessingStockStage.BUTTERMILK;
            case GHEE -> ProcessingStockStage.GHEE;
            default -> null;
        };
    }

    private ProcessingStockTxnResponse toResponse(ProcessingStockTxnEntity row) {
        return ProcessingStockTxnResponse.builder()
                .stockTxnId(row.getStockTxnId())
                .txnDate(row.getTxnDate())
                .txnType(row.getTxnType())
                .sourceKey(row.getSourceKey())
                .fromStage(row.getFromStage())
                .inputQty(row.getInputQty())
                .toStage(row.getToStage())
                .outputQty(row.getOutputQty())
                .notes(row.getNotes())
                .actorUsername(row.getActorUsername())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void upsertPipelineTasks(LocalDate date, double unsoldMilkLiters) {
        if (unsoldMilkLiters <= 0) {
            return;
        }
        double curdTargetKg = roundTo2(unsoldMilkLiters);
        double buttermilkPotential = roundTo2(unsoldMilkLiters * 0.72);
        double gheePotential = roundTo2(unsoldMilkLiters * 0.04);

        taskAutomationService.upsertStockPipelineTask(
                date,
                "CURD_SETUP",
                "EOD Milk to Curd Setup",
                String.format(
                        "Convert unsold milk to curd. Target: %.2f L milk -> %.2f kg curd.",
                        unsoldMilkLiters,
                        curdTargetKg
                ),
                UserRole.WORKER,
                TaskPriority.HIGH,
                LocalTime.of(20, 30)
        );
        taskAutomationService.upsertStockPipelineTask(
                date,
                "BUTTERMILK_PLAN",
                "Buttermilk Batch Planning",
                String.format(
                        "Plan churn from curd batches. Expected buttermilk potential: %.2f L.",
                        buttermilkPotential
                ),
                UserRole.WORKER,
                TaskPriority.MEDIUM,
                LocalTime.of(21, 0)
        );
        taskAutomationService.upsertStockPipelineTask(
                date,
                "GHEE_PLAN",
                "Ghee Batch Planning",
                String.format(
                        "Plan cream-to-ghee batch from today's curd flow. Expected ghee potential: %.2f kg.",
                        gheePotential
                ),
                UserRole.WORKER,
                TaskPriority.MEDIUM,
                LocalTime.of(21, 30)
        );
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
