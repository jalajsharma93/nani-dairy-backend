package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.health.MedicalTreatmentRepository;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.sales.dto.CustomerLedgerRowResponse;
import net.nani.dairy.sales.dto.CreateSaleRequest;
import net.nani.dairy.sales.dto.DeliveryChecklistItemResponse;
import net.nani.dairy.sales.dto.ReconcileSaleRequest;
import net.nani.dairy.sales.dto.SaleComplianceOverrideAuditResponse;
import net.nani.dairy.sales.dto.SaleResponse;
import net.nani.dairy.sales.dto.SettlementReconciliationRowResponse;
import net.nani.dairy.sales.dto.SalesSummaryResponse;
import net.nani.dairy.sales.dto.UpdateSaleDeliveryRequest;
import net.nani.dairy.sales.dto.UpdateSaleRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final MilkBatchRepository milkBatchRepository;
    private final MilkEntryRepository milkEntryRepository;
    private final MedicalTreatmentRepository medicalTreatmentRepository;
    private final AnimalRepository animalRepository;
    private final SaleComplianceOverrideAuditRepository saleComplianceOverrideAuditRepository;

    public List<SaleResponse> list(LocalDate date, CustomerType customerType, ProductType productType) {
        List<SaleEntity> rows;

        if (date != null && customerType != null && productType != null) {
            rows = saleRepository.findByDispatchDateAndCustomerTypeAndProductType(date, customerType, productType);
        } else if (date != null && customerType != null) {
            rows = saleRepository.findByDispatchDateAndCustomerType(date, customerType);
        } else if (date != null && productType != null) {
            rows = saleRepository.findByDispatchDateAndProductType(date, productType);
        } else if (customerType != null && productType != null) {
            rows = saleRepository.findByCustomerTypeAndProductType(customerType, productType);
        } else if (date != null) {
            rows = saleRepository.findByDispatchDate(date);
        } else if (customerType != null) {
            rows = saleRepository.findByCustomerType(customerType);
        } else if (productType != null) {
            rows = saleRepository.findByProductType(productType);
        } else {
            rows = saleRepository.findAll();
        }

        return rows.stream().map(this::toResponse).toList();
    }

    public SaleResponse create(CreateSaleRequest req, String actorUsername) {
        String saleId = buildId();
        validateMilkRule(
                req.getProductType(),
                req.getBatchDate(),
                req.getBatchShift(),
                req.getOverrideWithdrawalLock(),
                req.getOverrideReason(),
                saleId,
                req.getDispatchDate(),
                req.getCustomerName(),
                actorUsername,
                "CREATE"
        );

        SettlementPricing pricing = resolveSettlementPricing(
                req.getProductType(),
                req.getCustomerType(),
                req.getQuantity(),
                req.getUnitPrice(),
                req.getRouteName(),
                req.getCollectionPoint(),
                req.getFatPercent(),
                req.getSnfPercent(),
                req.getFatRatePerKg(),
                req.getSnfRatePerKg()
        );

        PaymentValues payment = computePayment(req.getQuantity(), pricing.effectiveUnitPrice(), req.getReceivedAmount());

        SaleEntity entity = SaleEntity.builder()
                .saleId(saleId)
                .dispatchDate(req.getDispatchDate())
                .customerType(req.getCustomerType())
                .customerName(req.getCustomerName().trim())
                .productType(req.getProductType())
                .quantity(req.getQuantity())
                .unitPrice(pricing.effectiveUnitPrice())
                .baseUnitPrice(req.getUnitPrice())
                .routeName(pricing.routeName())
                .collectionPoint(pricing.collectionPoint())
                .fatPercent(pricing.fatPercent())
                .snfPercent(pricing.snfPercent())
                .fatRatePerKg(pricing.fatRatePerKg())
                .snfRatePerKg(pricing.snfRatePerKg())
                .qualityPricingApplied(pricing.qualityPricingApplied())
                .settlementCycle(resolveSettlementCycle(req.getProductType(), req.getCustomerType(), req.getSettlementCycle()))
                .reconciled(false)
                .reconciledAt(null)
                .reconciledBy(null)
                .reconciliationNote(null)
                .totalAmount(payment.totalAmount)
                .receivedAmount(payment.receivedAmount)
                .pendingAmount(payment.pendingAmount)
                .paymentStatus(payment.paymentStatus)
                .paymentMode(req.getPaymentMode())
                .batchDate(req.getBatchDate())
                .batchShift(req.getBatchShift())
                .notes(trimToNull(req.getNotes()))
                .build();

        return toResponse(saleRepository.save(entity));
    }

    public SaleResponse update(String saleId, UpdateSaleRequest req, String actorUsername) {
        validateMilkRule(
                req.getProductType(),
                req.getBatchDate(),
                req.getBatchShift(),
                req.getOverrideWithdrawalLock(),
                req.getOverrideReason(),
                saleId,
                req.getDispatchDate(),
                req.getCustomerName(),
                actorUsername,
                "UPDATE"
        );

        SaleEntity entity = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        SettlementPricing pricing = resolveSettlementPricing(
                req.getProductType(),
                req.getCustomerType(),
                req.getQuantity(),
                req.getUnitPrice(),
                req.getRouteName(),
                req.getCollectionPoint(),
                req.getFatPercent(),
                req.getSnfPercent(),
                req.getFatRatePerKg(),
                req.getSnfRatePerKg()
        );

        PaymentValues payment = computePayment(req.getQuantity(), pricing.effectiveUnitPrice(), req.getReceivedAmount());

        entity.setDispatchDate(req.getDispatchDate());
        entity.setCustomerType(req.getCustomerType());
        entity.setCustomerName(req.getCustomerName().trim());
        entity.setProductType(req.getProductType());
        entity.setQuantity(req.getQuantity());
        entity.setUnitPrice(pricing.effectiveUnitPrice());
        entity.setBaseUnitPrice(req.getUnitPrice());
        entity.setRouteName(pricing.routeName());
        entity.setCollectionPoint(pricing.collectionPoint());
        entity.setFatPercent(pricing.fatPercent());
        entity.setSnfPercent(pricing.snfPercent());
        entity.setFatRatePerKg(pricing.fatRatePerKg());
        entity.setSnfRatePerKg(pricing.snfRatePerKg());
        entity.setQualityPricingApplied(pricing.qualityPricingApplied());
        entity.setSettlementCycle(resolveSettlementCycle(req.getProductType(), req.getCustomerType(), req.getSettlementCycle()));
        entity.setTotalAmount(payment.totalAmount);
        entity.setReceivedAmount(payment.receivedAmount);
        entity.setPendingAmount(payment.pendingAmount);
        entity.setPaymentStatus(payment.paymentStatus);
        entity.setPaymentMode(req.getPaymentMode());
        entity.setBatchDate(req.getBatchDate());
        entity.setBatchShift(req.getBatchShift());
        entity.setNotes(trimToNull(req.getNotes()));
        entity.setReconciled(false);
        entity.setReconciledAt(null);
        entity.setReconciledBy(null);
        entity.setReconciliationNote(null);

        return toResponse(saleRepository.save(entity));
    }

    public SalesSummaryResponse dailySummary(LocalDate date) {
        var rows = saleRepository.findByDispatchDate(date);

        double totalRevenue = 0;
        double milkRevenue = 0;
        double otherRevenue = 0;
        double totalReceived = 0;
        double totalPending = 0;

        for (var row : rows) {
            totalRevenue += row.getTotalAmount();
            totalReceived += row.getReceivedAmount();
            totalPending += row.getPendingAmount();

            if (row.getProductType() == ProductType.MILK) {
                milkRevenue += row.getTotalAmount();
            } else {
                otherRevenue += row.getTotalAmount();
            }
        }

        return new SalesSummaryResponse(
                date,
                totalRevenue,
                milkRevenue,
                otherRevenue,
                totalReceived,
                totalPending,
                rows.size()
        );
    }

    public List<CustomerLedgerRowResponse> ledger(LocalDate from, LocalDate to) {
        var rows = saleRepository.findByDispatchDateBetween(from, to);

        Map<String, MutableLedgerRow> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            String key = row.getCustomerType() + "__" + row.getCustomerName().trim();
            var acc = grouped.computeIfAbsent(
                    key,
                    k -> new MutableLedgerRow(row.getCustomerName().trim(), row.getCustomerType())
            );
            acc.totalAmount += row.getTotalAmount();
            acc.totalReceived += row.getReceivedAmount();
            acc.totalPending += row.getPendingAmount();
            acc.totalQuantity += row.getQuantity();
            acc.totalTransactions += 1;
        }

        return grouped.values().stream()
                .map(v -> new CustomerLedgerRowResponse(
                        v.customerName,
                        v.customerType,
                        v.totalAmount,
                        v.totalReceived,
                        v.totalPending,
                        v.totalQuantity,
                        v.totalTransactions
                ))
                .toList();
    }

    public List<SaleComplianceOverrideAuditResponse> overrideAudits(LocalDate from, LocalDate to) {
        return saleComplianceOverrideAuditRepository
                .findByDispatchDateBetweenOrderByCreatedAtDesc(from, to)
                .stream()
                .map(this::toOverrideAuditResponse)
                .toList();
    }

    public List<SettlementReconciliationRowResponse> reconciliation(LocalDate from, LocalDate to) {
        List<SaleEntity> rows = saleRepository.findByDispatchDateBetweenAndCustomerTypeAndProductType(
                from,
                to,
                CustomerType.COOPERATIVE,
                ProductType.MILK
        );

        Map<String, MutableReconciliationRow> grouped = new LinkedHashMap<>();
        for (SaleEntity row : rows) {
            SettlementCycle cycle = row.getSettlementCycle() != null ? row.getSettlementCycle() : SettlementCycle.MONTHLY;
            String key =
                    row.getCustomerType() + "__"
                            + sortable(row.getCustomerName()) + "__"
                            + sortable(row.getRouteName()) + "__"
                            + sortable(row.getCollectionPoint()) + "__"
                            + cycle;

            MutableReconciliationRow acc = grouped.computeIfAbsent(
                    key,
                    ignored -> new MutableReconciliationRow(
                            row.getCustomerName(),
                            row.getCustomerType(),
                            row.getRouteName(),
                            row.getCollectionPoint(),
                            cycle
                    )
            );

            acc.totalAmount += row.getTotalAmount();
            acc.totalReceived += row.getReceivedAmount();
            acc.totalPending += row.getPendingAmount();
            acc.totalQuantity += row.getQuantity();
            acc.totalTransactions += 1;
            if (Boolean.TRUE.equals(row.getReconciled())) {
                acc.reconciledTransactions += 1;
            } else {
                acc.unreconciledTransactions += 1;
            }
        }

        return grouped.values().stream()
                .sorted(
                        Comparator.comparing((MutableReconciliationRow row) -> sortable(row.routeName))
                                .thenComparing(row -> sortable(row.customerName))
                                .thenComparing(row -> row.settlementCycle.name())
                )
                .map(row -> new SettlementReconciliationRowResponse(
                        row.customerName,
                        row.customerType,
                        row.routeName,
                        row.collectionPoint,
                        row.settlementCycle,
                        row.totalAmount,
                        row.totalReceived,
                        row.totalPending,
                        row.totalQuantity,
                        row.totalTransactions,
                        row.reconciledTransactions,
                        row.unreconciledTransactions
                ))
                .toList();
    }

    public SaleResponse reconcile(String saleId, ReconcileSaleRequest req, String actorUsername) {
        SaleEntity entity = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        boolean canReconcile =
                entity.getProductType() == ProductType.MILK
                        && entity.getCustomerType() == CustomerType.COOPERATIVE;
        if (!canReconcile) {
            throw new IllegalArgumentException("Reconciliation is available only for cooperative milk sales");
        }

        boolean reconciled = Boolean.TRUE.equals(req.getReconciled());
        entity.setReconciled(reconciled);
        entity.setReconciliationNote(trimToNull(req.getNote()));

        if (reconciled) {
            entity.setReconciledAt(OffsetDateTime.now());
            entity.setReconciledBy(normalizeActor(actorUsername));
        } else {
            entity.setReconciledAt(null);
            entity.setReconciledBy(null);
        }

        return toResponse(saleRepository.save(entity));
    }

    public List<DeliveryChecklistItemResponse> deliveryList(LocalDate date) {
        return saleRepository
                .findByDispatchDate(date)
                .stream()
                .sorted(
                        Comparator.comparing((SaleEntity row) -> sortable(row.getRouteName()))
                                .thenComparing(row -> sortable(row.getCustomerName()))
                                .thenComparing(SaleEntity::getSaleId)
                )
                .map(this::toDeliveryChecklistItemResponse)
                .toList();
    }

    public DeliveryChecklistItemResponse updateDelivery(String saleId, UpdateSaleDeliveryRequest req, String actorUsername) {
        SaleEntity entity = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        boolean delivered = Boolean.TRUE.equals(req.getDelivered());
        entity.setDelivered(delivered);
        entity.setDeliveryNote(trimToNull(req.getDeliveryNote()));

        Double collectedAmount = req.getCollectedAmount();
        if (collectedAmount != null) {
            if (collectedAmount < 0) {
                throw new IllegalArgumentException("collectedAmount cannot be negative");
            }
            double nextReceived = entity.getReceivedAmount() + collectedAmount;
            if (nextReceived > entity.getTotalAmount()) {
                throw new IllegalArgumentException("collectedAmount exceeds pending amount");
            }
            double nextPending = entity.getTotalAmount() - nextReceived;
            entity.setReceivedAmount(nextReceived);
            entity.setPendingAmount(nextPending);
            entity.setPaymentStatus(paymentStatusFrom(entity.getTotalAmount(), nextReceived));
        }

        if (delivered) {
            entity.setDeliveredAt(OffsetDateTime.now());
            entity.setDeliveredBy(normalizeActor(actorUsername));
        } else {
            entity.setDeliveredAt(null);
            entity.setDeliveredBy(null);
        }

        return toDeliveryChecklistItemResponse(saleRepository.save(entity));
    }

    private void validateMilkRule(
            ProductType productType,
            LocalDate batchDate,
            net.nani.dairy.milk.Shift batchShift,
            Boolean overrideWithdrawalLock,
            String overrideReason,
            String saleId,
            LocalDate dispatchDate,
            String customerName,
            String actorUsername,
            String actionType
    ) {
        if (productType != ProductType.MILK) {
            return;
        }

        if (batchDate == null || batchShift == null) {
            throw new IllegalArgumentException("Milk sale requires batchDate and batchShift");
        }

        var batch = milkBatchRepository.findByDateAndShift(batchDate, batchShift)
                .orElseThrow(() -> new IllegalArgumentException("Milk batch not found for batchDate+batchShift"));

        if (batch.getQcStatus() != QcStatus.PASS) {
            throw new IllegalArgumentException("Only PASS QC milk batch can be sold");
        }

        enforceWithdrawalCompliance(
                batchDate,
                batchShift,
                Boolean.TRUE.equals(overrideWithdrawalLock),
                overrideReason,
                saleId,
                dispatchDate,
                customerName,
                actorUsername,
                actionType
        );
    }

    private void enforceWithdrawalCompliance(
            LocalDate batchDate,
            net.nani.dairy.milk.Shift batchShift,
            boolean overrideWithdrawalLock,
            String overrideReason,
            String saleId,
            LocalDate dispatchDate,
            String customerName,
            String actorUsername,
            String actionType
    ) {
        var batchEntries = milkEntryRepository.findByDateAndShift(batchDate, batchShift);
        List<String> animalIds = batchEntries.stream()
                .map(entry -> entry.getAnimalId() == null ? "" : entry.getAnimalId().trim())
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();

        if (animalIds.isEmpty()) {
            return;
        }

        var blockingTreatments =
                medicalTreatmentRepository
                        .findByAnimalIdInAndWithdrawalTillDateIsNotNullAndTreatmentDateLessThanEqualAndWithdrawalTillDateGreaterThanEqual(
                                animalIds,
                                batchDate,
                                batchDate
                        );

        if (blockingTreatments.isEmpty()) {
            return;
        }

        var blockedAnimalIds = blockingTreatments.stream()
                .map(treatment -> treatment.getAnimalId() == null ? "" : treatment.getAnimalId().trim())
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (blockedAnimalIds.isEmpty()) {
            return;
        }

        Map<String, String> tagsByAnimalId = animalRepository.findAllById(blockedAnimalIds)
                .stream()
                .collect(Collectors.toMap(AnimalEntity::getAnimalId, AnimalEntity::getTag, (first, second) -> first));

        String blockedPreview = blockedAnimalIds.stream()
                .map(id -> {
                    String tag = tagsByAnimalId.get(id);
                    return tag != null && !tag.isBlank() ? tag : id;
                })
                .limit(5)
                .collect(Collectors.joining(", "));

        if (blockedAnimalIds.size() > 5) {
            blockedPreview = blockedPreview + ", +" + (blockedAnimalIds.size() - 5) + " more";
        }

        if (!overrideWithdrawalLock) {
            throw new IllegalArgumentException(
                    "Milk sale blocked: withdrawal period active for animals " + blockedPreview
            );
        }

        String normalizedReason = trimToNull(overrideReason);
        if (normalizedReason == null) {
            throw new IllegalArgumentException("Withdrawal override reason is required");
        }

        String blockedAnimalIdsCsv = String.join(",", blockedAnimalIds);
        String blockedAnimalTagsCsv = blockedAnimalIds.stream()
                .map(id -> {
                    String tag = tagsByAnimalId.get(id);
                    return tag != null && !tag.isBlank() ? tag : id;
                })
                .collect(Collectors.joining(","));

        saleComplianceOverrideAuditRepository.save(
                SaleComplianceOverrideAuditEntity.builder()
                        .saleOverrideAuditId(buildOverrideAuditId())
                        .saleId(saleId)
                        .actionType(actionType)
                        .dispatchDate(dispatchDate)
                        .batchDate(batchDate)
                        .batchShift(batchShift)
                        .customerName(trimToNull(customerName) == null ? "UNKNOWN" : customerName.trim())
                        .actorUsername(normalizeActor(actorUsername))
                        .overrideReason(normalizedReason)
                        .blockedAnimalIds(blockedAnimalIdsCsv)
                        .blockedAnimalTags(blockedAnimalTagsCsv)
                        .build()
        );
    }

    private SettlementPricing resolveSettlementPricing(
            ProductType productType,
            CustomerType customerType,
            double quantity,
            double baseUnitPrice,
            String routeName,
            String collectionPoint,
            Double fatPercent,
            Double snfPercent,
            Double fatRatePerKg,
            Double snfRatePerKg
    ) {
        String normalizedRouteName = trimToNull(routeName);
        String normalizedCollectionPoint = trimToNull(collectionPoint);

        boolean isCooperativeMilk = productType == ProductType.MILK && customerType == CustomerType.COOPERATIVE;
        boolean hasQualityInputs =
                fatPercent != null || snfPercent != null || fatRatePerKg != null || snfRatePerKg != null;

        if (!isCooperativeMilk && hasQualityInputs) {
            throw new IllegalArgumentException("Fat/SNF settlement is supported only for cooperative milk sales");
        }

        if (!isCooperativeMilk) {
            return new SettlementPricing(
                    baseUnitPrice,
                    normalizedRouteName,
                    normalizedCollectionPoint,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        if (normalizedRouteName == null) {
            throw new IllegalArgumentException("Route name is required for cooperative milk sale");
        }

        if (!hasQualityInputs) {
            return new SettlementPricing(
                    baseUnitPrice,
                    normalizedRouteName,
                    normalizedCollectionPoint,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        if (fatPercent == null || snfPercent == null || fatRatePerKg == null || snfRatePerKg == null) {
            throw new IllegalArgumentException(
                    "Provide fat%, snf%, fat rate and snf rate together for cooperative settlement"
            );
        }

        if (fatPercent <= 0 || snfPercent <= 0 || fatRatePerKg <= 0 || snfRatePerKg <= 0) {
            throw new IllegalArgumentException("Fat/SNF values and rates must be positive numbers");
        }

        double fatKg = quantity * (fatPercent / 100.0);
        double snfKg = quantity * (snfPercent / 100.0);
        double qualityTotal = (fatKg * fatRatePerKg) + (snfKg * snfRatePerKg);
        double effectiveUnitPrice = qualityTotal / quantity;

        return new SettlementPricing(
                effectiveUnitPrice,
                normalizedRouteName,
                normalizedCollectionPoint,
                fatPercent,
                snfPercent,
                fatRatePerKg,
                snfRatePerKg,
                true
        );
    }

    private SettlementCycle resolveSettlementCycle(
            ProductType productType,
            CustomerType customerType,
            SettlementCycle requested
    ) {
        boolean isCooperativeMilk = productType == ProductType.MILK && customerType == CustomerType.COOPERATIVE;
        if (!isCooperativeMilk) {
            return null;
        }
        return requested != null ? requested : SettlementCycle.MONTHLY;
    }

    private PaymentValues computePayment(double quantity, double unitPrice, Double receivedAmountInput) {
        double total = quantity * unitPrice;
        double received = receivedAmountInput == null ? 0 : receivedAmountInput;
        if (received < 0) {
            throw new IllegalArgumentException("receivedAmount cannot be negative");
        }
        if (received > total) {
            throw new IllegalArgumentException("receivedAmount cannot be greater than totalAmount");
        }

        double pending = total - received;
        PaymentStatus status = paymentStatusFrom(total, received);

        return new PaymentValues(total, received, pending, status);
    }

    private PaymentStatus paymentStatusFrom(double totalAmount, double receivedAmount) {
        double pending = totalAmount - receivedAmount;
        if (pending == 0) {
            return PaymentStatus.PAID;
        }
        if (receivedAmount == 0) {
            return PaymentStatus.UNPAID;
        }
        return PaymentStatus.PARTIAL;
    }

    private String buildId() {
        return "SAL_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildOverrideAuditId() {
        return "SOV_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private SaleResponse toResponse(SaleEntity e) {
        return SaleResponse.builder()
                .saleId(e.getSaleId())
                .dispatchDate(e.getDispatchDate())
                .customerType(e.getCustomerType())
                .customerName(e.getCustomerName())
                .productType(e.getProductType())
                .quantity(e.getQuantity())
                .unitPrice(e.getUnitPrice())
                .baseUnitPrice(e.getBaseUnitPrice())
                .routeName(e.getRouteName())
                .collectionPoint(e.getCollectionPoint())
                .fatPercent(e.getFatPercent())
                .snfPercent(e.getSnfPercent())
                .fatRatePerKg(e.getFatRatePerKg())
                .snfRatePerKg(e.getSnfRatePerKg())
                .qualityPricingApplied(Boolean.TRUE.equals(e.getQualityPricingApplied()))
                .settlementCycle(e.getSettlementCycle())
                .reconciled(Boolean.TRUE.equals(e.getReconciled()))
                .reconciledAt(e.getReconciledAt())
                .reconciledBy(e.getReconciledBy())
                .reconciliationNote(e.getReconciliationNote())
                .delivered(Boolean.TRUE.equals(e.getDelivered()))
                .deliveredAt(e.getDeliveredAt())
                .deliveredBy(e.getDeliveredBy())
                .deliveryNote(e.getDeliveryNote())
                .totalAmount(e.getTotalAmount())
                .receivedAmount(e.getReceivedAmount())
                .pendingAmount(e.getPendingAmount())
                .paymentStatus(e.getPaymentStatus())
                .paymentMode(e.getPaymentMode() != null ? e.getPaymentMode() : PaymentMode.CASH)
                .batchDate(e.getBatchDate())
                .batchShift(e.getBatchShift())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private SaleComplianceOverrideAuditResponse toOverrideAuditResponse(SaleComplianceOverrideAuditEntity e) {
        return SaleComplianceOverrideAuditResponse.builder()
                .saleOverrideAuditId(e.getSaleOverrideAuditId())
                .saleId(e.getSaleId())
                .actionType(e.getActionType())
                .dispatchDate(e.getDispatchDate())
                .batchDate(e.getBatchDate())
                .batchShift(e.getBatchShift())
                .customerName(e.getCustomerName())
                .actorUsername(e.getActorUsername())
                .overrideReason(e.getOverrideReason())
                .blockedAnimalIds(e.getBlockedAnimalIds())
                .blockedAnimalTags(e.getBlockedAnimalTags())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private DeliveryChecklistItemResponse toDeliveryChecklistItemResponse(SaleEntity entity) {
        return DeliveryChecklistItemResponse.builder()
                .saleId(entity.getSaleId())
                .dispatchDate(entity.getDispatchDate())
                .customerName(entity.getCustomerName())
                .productType(entity.getProductType())
                .quantity(entity.getQuantity())
                .routeName(entity.getRouteName())
                .collectionPoint(entity.getCollectionPoint())
                .delivered(Boolean.TRUE.equals(entity.getDelivered()))
                .deliveredAt(entity.getDeliveredAt())
                .deliveredBy(entity.getDeliveredBy())
                .deliveryNote(entity.getDeliveryNote())
                .totalAmount(entity.getTotalAmount())
                .receivedAmount(entity.getReceivedAmount())
                .pendingAmount(entity.getPendingAmount())
                .paymentStatus(entity.getPaymentStatus())
                .build();
    }

    private String sortable(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record PaymentValues(double totalAmount, double receivedAmount, double pendingAmount, PaymentStatus paymentStatus) {
    }

    private record SettlementPricing(
            double effectiveUnitPrice,
            String routeName,
            String collectionPoint,
            Double fatPercent,
            Double snfPercent,
            Double fatRatePerKg,
            Double snfRatePerKg,
            boolean qualityPricingApplied
    ) {
    }

    private static class MutableLedgerRow {
        final String customerName;
        final CustomerType customerType;
        double totalAmount;
        double totalReceived;
        double totalPending;
        double totalQuantity;
        long totalTransactions;

        MutableLedgerRow(String customerName, CustomerType customerType) {
            this.customerName = customerName;
            this.customerType = customerType;
        }
    }

    private static class MutableReconciliationRow {
        final String customerName;
        final CustomerType customerType;
        final String routeName;
        final String collectionPoint;
        final SettlementCycle settlementCycle;
        double totalAmount;
        double totalReceived;
        double totalPending;
        double totalQuantity;
        long totalTransactions;
        long reconciledTransactions;
        long unreconciledTransactions;

        MutableReconciliationRow(
                String customerName,
                CustomerType customerType,
                String routeName,
                String collectionPoint,
                SettlementCycle settlementCycle
        ) {
            this.customerName = customerName;
            this.customerType = customerType;
            this.routeName = routeName;
            this.collectionPoint = collectionPoint;
            this.settlementCycle = settlementCycle;
        }
    }
}
