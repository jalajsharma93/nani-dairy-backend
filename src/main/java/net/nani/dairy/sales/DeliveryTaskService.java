package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.dto.AddDeliveryTaskAddonRequest;
import net.nani.dairy.sales.dto.CreateDeliveryTaskRequest;
import net.nani.dairy.sales.dto.CreateDeliveryRunClosureRequest;
import net.nani.dairy.sales.dto.CreateSaleRequest;
import net.nani.dairy.sales.dto.DeliveryDayPlanTriggerResponse;
import net.nani.dairy.sales.dto.DeliveryReconciliationRowResponse;
import net.nani.dairy.sales.dto.DeliveryRunClosureResponse;
import net.nani.dairy.sales.dto.DeliveryRouteOptimizationResponse;
import net.nani.dairy.sales.dto.DeliveryTaskResponse;
import net.nani.dairy.sales.dto.SaleResponse;
import net.nani.dairy.sales.dto.SubscriptionGenerationPreviewItemResponse;
import net.nani.dairy.sales.dto.SubscriptionGenerationPreviewResponse;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskAssigneeRequest;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkItemRequest;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkItemResponse;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkRequest;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkResponse;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusRequest;
import net.nani.dairy.stock.ProcessingStockService;
import net.nani.dairy.stock.dto.ProcessingStockSummaryResponse;
import net.nani.dairy.stock.dto.SyncProcessingDayRequest;
import net.nani.dairy.tasks.TaskAutomationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryTaskService {

    private static final DateTimeFormatter SOURCE_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final LocalTime AM_ROUTE_START = LocalTime.of(5, 30);
    private static final LocalTime PM_ROUTE_START = LocalTime.of(16, 30);
    private static final int DELIVERY_SLOT_MINUTES = 15;
    private static final int SLA_GRACE_MINUTES = 45;

    private static final String REASON_TASK_ALREADY_EXISTS_PENDING = "TASK_ALREADY_EXISTS_PENDING";
    private static final String REASON_TASK_ALREADY_EXISTS_DELIVERED = "TASK_ALREADY_EXISTS_DELIVERED";
    private static final String REASON_TASK_ALREADY_EXISTS_SKIPPED = "TASK_ALREADY_EXISTS_SKIPPED";

    private final DeliveryTaskRepository deliveryTaskRepository;
    private final CustomerRecordRepository customerRecordRepository;
    private final CustomerSubscriptionLineRepository subscriptionLineRepository;
    private final DeliveryRunClosureRepository deliveryRunClosureRepository;
    private final MilkBatchRepository milkBatchRepository;
    private final SaleRepository saleRepository;
    private final SaleService saleService;
    private final AuthUserRepository authUserRepository;
    private final TaskAutomationService taskAutomationService;
    private final ProcessingStockService processingStockService;

    @Transactional
    public List<DeliveryTaskResponse> list(
            LocalDate date,
            DeliveryTaskStatus status,
            String actorUsername,
            boolean privilegedActor
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String normalizedActor = normalizeActor(actorUsername);
        generateSubscriptionTasks(effectiveDate, normalizedActor);
        optimizeRoutesIfNeeded(effectiveDate, normalizedActor);
        List<DeliveryTaskEntity> rows = status == null
                ? deliveryTaskRepository.findByTaskDate(effectiveDate)
                : deliveryTaskRepository.findByTaskDateAndStatus(effectiveDate, status);

        if (!privilegedActor) {
            rows = rows.stream()
                    .filter(row -> {
                        String assignee = trimToNull(row.getAssignedToUsername());
                        return assignee == null || assignee.equalsIgnoreCase(normalizedActor);
                    })
                    .toList();
        }

        return rows.stream()
                .sorted(deliverySort())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeliveryRouteOptimizationResponse optimizeRoutes(
            LocalDate date,
            Shift shift,
            String routeName,
            String actorUsername
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String normalizedRouteName = trimToNull(routeName);
        String actor = normalizeActor(actorUsername);
        OffsetDateTime optimizedAt = OffsetDateTime.now();

        List<DeliveryTaskEntity> scopedRows = deliveryTaskRepository.findByTaskDate(effectiveDate).stream()
                .filter(row -> shift == null || (row.getTaskShift() != null ? row.getTaskShift() : Shift.AM) == shift)
                .filter(row -> normalizedRouteName == null
                        || sortable(row.getRouteName()).equals(sortable(normalizedRouteName)))
                .toList();

        if (scopedRows.isEmpty()) {
            return DeliveryRouteOptimizationResponse.builder()
                    .date(effectiveDate)
                    .shift(shift)
                    .routeName(normalizedRouteName)
                    .optimizedTasks(0)
                    .optimizedRoutes(0)
                    .pendingTasksInScope(0)
                    .deliveredTasksInScope(0)
                    .actor(actor)
                    .optimizedAt(optimizedAt)
                    .build();
        }

        long pendingTasks = scopedRows.stream()
                .filter(row -> (row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus()) == DeliveryTaskStatus.PENDING)
                .count();
        long deliveredTasks = scopedRows.stream()
                .filter(row -> (row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus()) == DeliveryTaskStatus.DELIVERED)
                .count();

        Map<RouteScopeKey, List<DeliveryTaskEntity>> byScope = new LinkedHashMap<>();
        for (DeliveryTaskEntity row : scopedRows) {
            Shift rowShift = row.getTaskShift() != null ? row.getTaskShift() : Shift.AM;
            String rowRoute = trimToNull(row.getRouteName());
            RouteScopeKey key = new RouteScopeKey(
                    rowRoute == null ? "UNASSIGNED_ROUTE" : rowRoute,
                    rowShift
            );
            byScope.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        int optimizedTasks = 0;
        int optimizedRoutes = 0;
        List<DeliveryTaskEntity> updates = new ArrayList<>();

        for (Map.Entry<RouteScopeKey, List<DeliveryTaskEntity>> entry : byScope.entrySet()) {
            List<DeliveryTaskEntity> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }
            optimizedRoutes += 1;
            rows.sort(routeOptimizationSort());

            LocalTime cursor = routeStartTime(entry.getKey().shift());
            int stopNo = 1;
            for (DeliveryTaskEntity row : rows) {
                LocalTime preferred = row.getPreferredTime();
                LocalTime eta = preferred != null && preferred.isAfter(cursor) ? preferred : cursor;
                LocalTime due = (preferred != null ? preferred : eta).plusMinutes(SLA_GRACE_MINUTES);

                row.setOptimizedStopOrder(stopNo);
                row.setPlannedEta(eta);
                row.setSlaDueTime(due);
                row.setOptimizedAt(optimizedAt);
                applySlaOutcome(row);

                stopNo += 1;
                optimizedTasks += 1;
                cursor = eta.plusMinutes(DELIVERY_SLOT_MINUTES);
                updates.add(row);
            }
        }

        if (!updates.isEmpty()) {
            deliveryTaskRepository.saveAll(updates);
        }

        return DeliveryRouteOptimizationResponse.builder()
                .date(effectiveDate)
                .shift(shift)
                .routeName(normalizedRouteName)
                .optimizedTasks(optimizedTasks)
                .optimizedRoutes(optimizedRoutes)
                .pendingTasksInScope(pendingTasks)
                .deliveredTasksInScope(deliveredTasks)
                .actor(actor)
                .optimizedAt(optimizedAt)
                .build();
    }

    @Transactional
    public int generateSubscriptionTasks(LocalDate date, String actorUsername) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String actor = normalizeActor(actorUsername);

        List<CustomerRecordEntity> customers = customerRecordRepository.findByIsActiveAndSubscriptionActive(true, true);
        Map<String, CustomerRecordEntity> customerById = new HashMap<>();
        for (CustomerRecordEntity customer : customers) {
            customerById.put(customer.getCustomerId(), customer);
        }

        int generated = 0;
        Set<String> customersWithLinePlans = subscriptionLineRepository.findByActiveTrue().stream()
                .map(CustomerSubscriptionLineEntity::getCustomerId)
                .filter(customerById::containsKey)
                .collect(java.util.stream.Collectors.toSet());

        List<CustomerSubscriptionLineEntity> activeLines = subscriptionLineRepository.findByActiveTrue();
        for (CustomerSubscriptionLineEntity line : activeLines) {
            CustomerRecordEntity customer = customerById.get(line.getCustomerId());
            if (customer == null || !customer.isSubscriptionActive()) {
                continue;
            }
            if (!shouldGenerateForDate(
                    customer,
                    effectiveDate,
                    line.getActiveDaysCsv(),
                    line.getStartDate(),
                    line.getEndDate()
            )) {
                continue;
            }

            double qty = line.getQuantity();
            double unitPrice = line.getUnitPrice() > 0
                    ? line.getUnitPrice()
                    : safeDouble(customer.getDefaultMilkUnitPrice());
            if (qty <= 0 || unitPrice <= 0) {
                continue;
            }

            generated += upsertSubscriptionTask(
                    customer,
                    effectiveDate,
                    line.getTaskShift(),
                    line.getProductType(),
                    line.getPreferredTime(),
                    qty,
                    unitPrice,
                    actor,
                    line.getActiveDaysCsv(),
                    line.getStartDate(),
                    line.getEndDate()
            );
        }

        // Backward-compatible fallback: customers with legacy daily qty but no subscription line rows.
        for (CustomerRecordEntity customer : customers) {
            if (customersWithLinePlans.contains(customer.getCustomerId())) {
                continue;
            }
            if (!shouldGenerateForDate(customer, effectiveDate, null, null, null)) {
                continue;
            }
            double totalQty = safeDouble(customer.getDailySubscriptionQty());
            double unitPrice = safeDouble(customer.getDefaultMilkUnitPrice());
            if (totalQty <= 0 || unitPrice <= 0) {
                continue;
            }

            double amQty = roundTo2(totalQty / 2.0);
            double pmQty = roundTo2(Math.max(0, totalQty - amQty));
            generated += upsertSubscriptionTask(
                    customer,
                    effectiveDate,
                    Shift.AM,
                    ProductType.MILK,
                    null,
                    amQty,
                    unitPrice,
                    actor,
                    null,
                    null,
                    null
            );
            generated += upsertSubscriptionTask(
                    customer,
                    effectiveDate,
                    Shift.PM,
                    ProductType.MILK,
                    null,
                    pmQty,
                    unitPrice,
                    actor,
                    null,
                    null,
                    null
            );
        }

        return generated;
    }

    @Transactional
    public DeliveryDayPlanTriggerResponse triggerDayPlan(
            LocalDate date,
            String actorUsername,
            boolean autoAssign,
            boolean optimize
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String actor = normalizeActor(actorUsername);
        SubscriptionGenerationPreviewResponse preview = previewSubscriptionGeneration(effectiveDate);
        int eligibleCandidates = preview.getEligibleCandidates();
        int alreadyPlannedCandidates = (int) preview.getItems().stream()
                .filter(item -> !item.isEligible())
                .map(SubscriptionGenerationPreviewItemResponse::getReason)
                .filter(this::isExistingTaskReason)
                .count();
        int blockedCandidates = Math.max(0, preview.getSkippedCandidates() - alreadyPlannedCandidates);

        int generated = generateSubscriptionTasks(effectiveDate, actor);
        int autoAssigned = autoAssign ? autoAssignPendingTasks(effectiveDate, actor) : 0;
        DeliveryRouteOptimizationResponse optimization = optimize
                ? optimizeRoutes(effectiveDate, null, null, actor)
                : DeliveryRouteOptimizationResponse.builder()
                .date(effectiveDate)
                .optimizedTasks(0)
                .optimizedRoutes(0)
                .pendingTasksInScope(0)
                .deliveredTasksInScope(0)
                .actor(actor)
                .optimizedAt(OffsetDateTime.now())
                .build();

        List<DeliveryTaskEntity> rows = deliveryTaskRepository.findByTaskDate(effectiveDate);
        long total = rows.size();
        long pending = rows.stream()
                .filter(row -> (row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus()) == DeliveryTaskStatus.PENDING)
                .count();
        long unassignedPending = rows.stream()
                .filter(row -> (row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus()) == DeliveryTaskStatus.PENDING)
                .filter(row -> trimToNull(row.getAssignedToUsername()) == null)
                .count();

        return DeliveryDayPlanTriggerResponse.builder()
                .date(effectiveDate)
                .generatedTasks(generated)
                .eligibleCandidates(eligibleCandidates)
                .alreadyPlannedCandidates(alreadyPlannedCandidates)
                .blockedCandidates(blockedCandidates)
                .autoAssignedTasks(autoAssigned)
                .optimizedTasks(optimization.getOptimizedTasks())
                .optimizedRoutes(optimization.getOptimizedRoutes())
                .totalTasks(total)
                .pendingTasks(pending)
                .unassignedPendingTasks(unassignedPending)
                .actor(actor)
                .build();
    }

    @Transactional(readOnly = true)
    public SubscriptionGenerationPreviewResponse previewSubscriptionGeneration(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        List<CustomerRecordEntity> customers = customerRecordRepository.findByIsActiveAndSubscriptionActive(true, true);
        Map<String, CustomerRecordEntity> customerById = new HashMap<>();
        for (CustomerRecordEntity customer : customers) {
            customerById.put(customer.getCustomerId(), customer);
        }

        List<SubscriptionGenerationPreviewItemResponse> items = new ArrayList<>();

        Set<String> customersWithLinePlans = subscriptionLineRepository.findByActiveTrue().stream()
                .map(CustomerSubscriptionLineEntity::getCustomerId)
                .filter(customerById::containsKey)
                .collect(java.util.stream.Collectors.toSet());

        List<CustomerSubscriptionLineEntity> activeLines = subscriptionLineRepository.findByActiveTrue();
        for (CustomerSubscriptionLineEntity line : activeLines) {
            CustomerRecordEntity customer = customerById.get(line.getCustomerId());
            String reason = generationBlockReason(
                    customer,
                    effectiveDate,
                    line.getActiveDaysCsv(),
                    line.getStartDate(),
                    line.getEndDate()
            );

            double qty = line.getQuantity();
            double unitPrice = line.getUnitPrice() > 0
                    ? line.getUnitPrice()
                    : (customer != null ? safeDouble(customer.getDefaultMilkUnitPrice()) : 0.0);

            if (reason == null) {
                if (qty <= 0) {
                    reason = "LINE_QTY_NOT_POSITIVE";
                } else if (unitPrice <= 0) {
                    reason = "LINE_UNIT_PRICE_NOT_POSITIVE";
                }
            }

            if (reason == null && customer != null) {
                String sourceRefId = buildSubscriptionSourceRef(
                        customer.getCustomerId(),
                        effectiveDate,
                        line.getTaskShift() != null ? line.getTaskShift() : Shift.AM,
                        line.getProductType() != null ? line.getProductType() : ProductType.MILK,
                        line.getPreferredTime()
                );
                reason = existingTaskReason(sourceRefId);
            }

            items.add(SubscriptionGenerationPreviewItemResponse.builder()
                    .source("LINE")
                    .date(effectiveDate)
                    .customerId(customer != null ? customer.getCustomerId() : line.getCustomerId())
                    .customerName(customer != null ? customer.getCustomerName() : "Unknown")
                    .routeName(customer != null ? trimToNull(customer.getRouteName()) : null)
                    .subscriptionLineId(line.getSubscriptionLineId())
                    .shift(line.getTaskShift())
                    .productType(line.getProductType())
                    .quantity(roundTo2(qty))
                    .unitPrice(roundTo2(unitPrice))
                    .activeDaysCsv(line.getActiveDaysCsv())
                    .startDate(line.getStartDate())
                    .endDate(line.getEndDate())
                    .eligible(reason == null)
                    .reason(reason)
                    .build());
        }

        for (CustomerRecordEntity customer : customers) {
            if (customersWithLinePlans.contains(customer.getCustomerId())) {
                continue;
            }
            String customerRuleReason = generationBlockReason(customer, effectiveDate, null, null, null);
            double totalQty = safeDouble(customer.getDailySubscriptionQty());
            double unitPrice = safeDouble(customer.getDefaultMilkUnitPrice());
            double amQty = roundTo2(totalQty / 2.0);
            double pmQty = roundTo2(Math.max(0, totalQty - amQty));

            String amReason = customerRuleReason;
            if (amReason == null) {
                if (amQty <= 0) {
                    amReason = "LEGACY_QTY_NOT_POSITIVE";
                } else if (unitPrice <= 0) {
                    amReason = "LEGACY_UNIT_PRICE_NOT_POSITIVE";
                }
            }
            if (amReason == null) {
                String amSourceRefId = buildSubscriptionSourceRef(customer.getCustomerId(), effectiveDate, Shift.AM, ProductType.MILK, null);
                amReason = existingTaskReason(amSourceRefId);
            }
            items.add(SubscriptionGenerationPreviewItemResponse.builder()
                    .source("LEGACY")
                    .date(effectiveDate)
                    .customerId(customer.getCustomerId())
                    .customerName(customer.getCustomerName())
                    .routeName(trimToNull(customer.getRouteName()))
                    .subscriptionLineId(null)
                    .shift(Shift.AM)
                    .productType(ProductType.MILK)
                    .quantity(amQty)
                    .unitPrice(roundTo2(unitPrice))
                    .activeDaysCsv(null)
                    .startDate(null)
                    .endDate(null)
                    .eligible(amReason == null)
                    .reason(amReason)
                    .build());

            String pmReason = customerRuleReason;
            if (pmReason == null) {
                if (pmQty <= 0) {
                    pmReason = "LEGACY_QTY_NOT_POSITIVE";
                } else if (unitPrice <= 0) {
                    pmReason = "LEGACY_UNIT_PRICE_NOT_POSITIVE";
                }
            }
            if (pmReason == null) {
                String pmSourceRefId = buildSubscriptionSourceRef(customer.getCustomerId(), effectiveDate, Shift.PM, ProductType.MILK, null);
                pmReason = existingTaskReason(pmSourceRefId);
            }
            items.add(SubscriptionGenerationPreviewItemResponse.builder()
                    .source("LEGACY")
                    .date(effectiveDate)
                    .customerId(customer.getCustomerId())
                    .customerName(customer.getCustomerName())
                    .routeName(trimToNull(customer.getRouteName()))
                    .subscriptionLineId(null)
                    .shift(Shift.PM)
                    .productType(ProductType.MILK)
                    .quantity(pmQty)
                    .unitPrice(roundTo2(unitPrice))
                    .activeDaysCsv(null)
                    .startDate(null)
                    .endDate(null)
                    .eligible(pmReason == null)
                    .reason(pmReason)
                    .build());
        }

        int eligibleCount = (int) items.stream().filter(SubscriptionGenerationPreviewItemResponse::isEligible).count();
        int total = items.size();
        return SubscriptionGenerationPreviewResponse.builder()
                .date(effectiveDate)
                .totalCandidates(total)
                .eligibleCandidates(eligibleCount)
                .skippedCandidates(Math.max(0, total - eligibleCount))
                .items(items)
                .build();
    }

    @Transactional
    public DeliveryTaskResponse create(CreateDeliveryTaskRequest req, String actorUsername) {
        String normalizedActor = normalizeActor(actorUsername);
        CustomerRecordEntity linkedCustomer = resolveLinkedCustomer(req.getCustomerId(), req.getCustomerName());
        String normalizedAssignedTo = trimToNull(req.getAssignedToUsername());

        String finalCustomerName = linkedCustomer != null ? linkedCustomer.getCustomerName() : trimToNull(req.getCustomerName());
        if (finalCustomerName == null) {
            throw new IllegalArgumentException("customerId or customerName is required");
        }

        Double effectiveUnitPrice = req.getUnitPrice();
        if (effectiveUnitPrice == null && linkedCustomer != null) {
            effectiveUnitPrice = linkedCustomer.getDefaultMilkUnitPrice();
        }
        if (effectiveUnitPrice == null || effectiveUnitPrice <= 0) {
            throw new IllegalArgumentException("unitPrice is required (or set defaultMilkUnitPrice on customer)");
        }

        if (normalizedAssignedTo != null) {
            authUserRepository.findByUsernameIgnoreCase(normalizedAssignedTo)
                    .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        }

        Shift shift = req.getTaskShift() != null ? req.getTaskShift() : Shift.AM;
        ProductType productType = req.getProductType() != null ? req.getProductType() : ProductType.MILK;

        DeliveryTaskEntity entity = DeliveryTaskEntity.builder()
                .deliveryTaskId(buildId())
                .taskDate(req.getTaskDate())
                .customerId(linkedCustomer != null ? linkedCustomer.getCustomerId() : null)
                .customerName(finalCustomerName)
                .assignedToUsername(normalizedAssignedTo)
                .assignedByUsername(normalizedAssignedTo != null ? normalizedActor : null)
                .assignedAt(normalizedAssignedTo != null ? OffsetDateTime.now() : null)
                .routeName(linkedCustomer != null ? linkedCustomer.getRouteName() : null)
                .productType(productType)
                .taskShift(shift)
                .preferredTime(req.getPreferredTime())
                .plannedQtyLiters(req.getPlannedQtyLiters())
                .unitPrice(effectiveUnitPrice)
                .paymentMode(req.getPaymentMode() != null ? req.getPaymentMode() : PaymentMode.CREDIT)
                .deliveredQtyLiters(null)
                .saleId(null)
                .saleRecordedAt(null)
                .status(DeliveryTaskStatus.PENDING)
                .autoGenerated(false)
                .sourceRefId(null)
                .notes(trimToNull(req.getNotes()))
                .createdBy(normalizedActor)
                .completedBy(null)
                .completedAt(null)
                .build();

        DeliveryTaskEntity saved = deliveryTaskRepository.save(entity);
        taskAutomationService.upsertFromDeliveryTask(saved);
        return toResponse(saved);
    }

    @Transactional
    public DeliveryTaskResponse addAddon(AddDeliveryTaskAddonRequest req, String actorUsername) {
        String normalizedActor = normalizeActor(actorUsername);
        CustomerRecordEntity linkedCustomer = resolveLinkedCustomer(req.getCustomerId(), req.getCustomerName());

        String customerName = linkedCustomer != null ? linkedCustomer.getCustomerName() : trimToNull(req.getCustomerName());
        if (customerName == null) {
            throw new IllegalArgumentException("customerId or customerName is required");
        }

        String customerId = linkedCustomer != null ? linkedCustomer.getCustomerId() : null;
        ProductType productType = req.getProductType() != null ? req.getProductType() : ProductType.MILK;
        Shift shift = req.getTaskShift() != null ? req.getTaskShift() : Shift.AM;
        LocalTime preferredTime = req.getPreferredTime();

        Double unitPrice = req.getUnitPrice();
        if ((unitPrice == null || unitPrice <= 0) && linkedCustomer != null) {
            unitPrice = linkedCustomer.getDefaultMilkUnitPrice();
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new IllegalArgumentException("unitPrice is required (or set defaultMilkUnitPrice on customer)");
        }

        DeliveryTaskEntity existing = findPendingMergeTarget(req.getTaskDate(), customerId, customerName, shift, productType, preferredTime);
        if (existing != null && trimToNull(existing.getSaleId()) == null) {
            existing.setPlannedQtyLiters(roundTo2(existing.getPlannedQtyLiters() + req.getQuantity()));
            existing.setUnitPrice(unitPrice);
            existing.setPaymentMode(req.getPaymentMode() != null ? req.getPaymentMode() : existing.getPaymentMode());
            existing.setRouteName(linkedCustomer != null ? linkedCustomer.getRouteName() : existing.getRouteName());
            existing.setNotes(mergeNotes(existing.getNotes(), req.getNotes(), normalizedActor));
            DeliveryTaskEntity saved = deliveryTaskRepository.save(existing);
            taskAutomationService.upsertFromDeliveryTask(saved);
            return toResponse(saved);
        }

        DeliveryTaskEntity created = DeliveryTaskEntity.builder()
                .deliveryTaskId(buildId())
                .taskDate(req.getTaskDate())
                .customerId(customerId)
                .customerName(customerName)
                .assignedToUsername(null)
                .assignedByUsername(normalizedActor)
                .assignedAt(OffsetDateTime.now())
                .routeName(linkedCustomer != null ? linkedCustomer.getRouteName() : null)
                .productType(productType)
                .taskShift(shift)
                .preferredTime(preferredTime)
                .plannedQtyLiters(req.getQuantity())
                .unitPrice(unitPrice)
                .paymentMode(req.getPaymentMode() != null ? req.getPaymentMode() : PaymentMode.CREDIT)
                .status(DeliveryTaskStatus.PENDING)
                .autoGenerated(false)
                .sourceRefId("ADDON:" + UUID.randomUUID().toString().substring(0, 10))
                .notes(mergeNotes(null, req.getNotes(), normalizedActor))
                .createdBy(normalizedActor)
                .build();

        DeliveryTaskEntity saved = deliveryTaskRepository.save(created);
        taskAutomationService.upsertFromDeliveryTask(saved);
        return toResponse(saved);
    }

    @Transactional
    public DeliveryRunClosureResponse recordRunClosure(
            CreateDeliveryRunClosureRequest req,
            String actorUsername,
            boolean privilegedActor
    ) {
        String actor = normalizeActor(actorUsername);
        String routeName = trimToNull(req.getRouteName());
        if (routeName == null) {
            throw new IllegalArgumentException("routeName is required");
        }
        if (req.getDate() == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (req.getShift() == null) {
            throw new IllegalArgumentException("shift is required");
        }

        long totalStops = req.getTotalStops() == null ? 0L : req.getTotalStops();
        long deliveredStops = req.getDeliveredStops() == null ? 0L : req.getDeliveredStops();
        long pendingStops = req.getPendingStops() == null ? 0L : req.getPendingStops();
        long skippedStops = req.getSkippedStops() == null ? 0L : req.getSkippedStops();
        if (deliveredStops + pendingStops + skippedStops != totalStops) {
            throw new IllegalArgumentException("Stop counts do not match totalStops");
        }
        String note = trimToNull(req.getNotes());
        if (!privilegedActor && pendingStops > 0) {
            throw new IllegalArgumentException("Run cannot be closed while pending stops exist");
        }
        if (privilegedActor && pendingStops > 0 && note == null) {
            throw new IllegalArgumentException("Add closure note when closing run with pending stops");
        }

        double expectedCollection = safeDouble(req.getExpectedCollection());
        double actualCollection = safeDouble(req.getActualCollection());
        double cashCollection = safeDouble(req.getCashCollection());
        double upiCollection = safeDouble(req.getUpiCollection());
        double otherCollection = safeDouble(req.getOtherCollection());
        double variance = roundTo2(actualCollection - expectedCollection);

        DeliveryRunClosureEntity entity = DeliveryRunClosureEntity.builder()
                .runClosureId("DRC_" + UUID.randomUUID().toString().substring(0, 8))
                .date(req.getDate())
                .routeName(routeName)
                .shift(req.getShift())
                .totalStops(totalStops)
                .deliveredStops(deliveredStops)
                .pendingStops(pendingStops)
                .skippedStops(skippedStops)
                .expectedCollection(roundTo2(expectedCollection))
                .actualCollection(roundTo2(actualCollection))
                .variance(variance)
                .cashCollection(roundTo2(cashCollection))
                .upiCollection(roundTo2(upiCollection))
                .otherCollection(roundTo2(otherCollection))
                .notes(note)
                .closedBy(actor)
                .closedAt(OffsetDateTime.now())
                .build();

        DeliveryRunClosureEntity saved = deliveryRunClosureRepository.save(entity);
        StockClosureState closureState = evaluateStockClosureState(req.getDate());

        boolean autoTransferTriggered = false;
        double autoTransferredLiters = 0;
        if (closureState.bothShiftsClosed() && closureState.pendingMilkToCurdLiters() > 0) {
            autoTransferredLiters = closureState.pendingMilkToCurdLiters();
            processingStockService.syncDay(
                    SyncProcessingDayRequest.builder()
                            .date(req.getDate())
                            .autoTransferMilkToCurd(true)
                            .build(),
                    actor
            );
            autoTransferTriggered = true;
            closureState = evaluateStockClosureState(req.getDate());
        }

        return toRunClosureResponse(saved, closureState, autoTransferTriggered, autoTransferredLiters);
    }

    public List<DeliveryRunClosureResponse> listRunClosures(
            LocalDate date,
            String actorUsername,
            boolean privilegedActor
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String actor = normalizeActor(actorUsername);
        List<DeliveryRunClosureEntity> rows = deliveryRunClosureRepository.findByDateOrderByClosedAtDesc(effectiveDate);
        if (!privilegedActor) {
            rows = rows.stream()
                    .filter(row -> actor.equalsIgnoreCase(trimToNull(row.getClosedBy())))
                    .toList();
        }
        StockClosureState closureState = evaluateStockClosureState(effectiveDate);
        return rows.stream()
                .map(row -> toRunClosureResponse(row, closureState, false, 0))
                .toList();
    }

    @Transactional
    public DeliveryTaskResponse assignTask(
            String deliveryTaskId,
            UpdateDeliveryTaskAssigneeRequest req,
            String actorUsername
    ) {
        DeliveryTaskEntity entity = deliveryTaskRepository.findById(deliveryTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery task not found"));
        String normalizedActor = normalizeActor(actorUsername);
        String nextAssignee = trimToNull(req.getAssignedToUsername());

        if (entity.getStatus() == DeliveryTaskStatus.DELIVERED) {
            throw new IllegalArgumentException("Delivered task cannot be reassigned");
        }

        if (nextAssignee != null) {
            authUserRepository.findByUsernameIgnoreCase(nextAssignee)
                    .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        }

        entity.setAssignedToUsername(nextAssignee);
        entity.setAssignedByUsername(nextAssignee != null ? normalizedActor : null);
        entity.setAssignedAt(nextAssignee != null ? OffsetDateTime.now() : null);

        if (req.getNotes() != null) {
            entity.setNotes(trimToNull(req.getNotes()));
        }

        DeliveryTaskEntity saved = deliveryTaskRepository.save(entity);
        taskAutomationService.upsertFromDeliveryTask(saved);
        return toResponse(saved);
    }

    @Transactional
    public DeliveryTaskResponse updateStatus(
            String deliveryTaskId,
            UpdateDeliveryTaskStatusRequest req,
            String actorUsername,
            boolean privilegedActor
    ) {
        DeliveryTaskEntity entity = deliveryTaskRepository.findById(deliveryTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery task not found"));
        String normalizedActor = normalizeActor(actorUsername);
        String currentAssignee = trimToNull(entity.getAssignedToUsername());

        if (!privilegedActor) {
            if (currentAssignee != null && !currentAssignee.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("Task is assigned to another user");
            }
            if (currentAssignee == null) {
                entity.setAssignedToUsername(normalizedActor);
                entity.setAssignedByUsername("system");
                entity.setAssignedAt(OffsetDateTime.now());
            }
        }

        boolean saleAlreadyRecorded = trimToNull(entity.getSaleId()) != null;
        if (saleAlreadyRecorded && req.getDeliveredQtyLiters() != null) {
            double existingDelivered = entity.getDeliveredQtyLiters() == null ? 0.0 : entity.getDeliveredQtyLiters();
            if (Double.compare(existingDelivered, req.getDeliveredQtyLiters()) != 0) {
                throw new IllegalArgumentException("Sale already recorded for this task; quantity cannot be changed");
            }
        }

        DeliveryTaskStatus nextStatus = req.getStatus() != null ? req.getStatus() : entity.getStatus();
        if (nextStatus == null) {
            nextStatus = DeliveryTaskStatus.PENDING;
        }
        entity.setStatus(nextStatus);

        if (req.getDeliveredQtyLiters() != null) {
            if (req.getDeliveredQtyLiters() < 0) {
                throw new IllegalArgumentException("deliveredQtyLiters cannot be negative");
            }
            entity.setDeliveredQtyLiters(req.getDeliveredQtyLiters());
        }

        boolean overrideWithdrawalLock = Boolean.TRUE.equals(req.getOverrideWithdrawalLock());
        if (overrideWithdrawalLock && !privilegedActor) {
            throw new IllegalArgumentException("Only ADMIN/MANAGER can override withdrawal lock");
        }

        if (nextStatus == DeliveryTaskStatus.DELIVERED) {
            if (entity.getDeliveredQtyLiters() == null) {
                entity.setDeliveredQtyLiters(entity.getPlannedQtyLiters());
            }
            if (entity.getDeliveredQtyLiters() <= 0) {
                throw new IllegalArgumentException("Delivered quantity must be positive");
            }
            validateMilkDeliveryEligibility(entity, entity.getDeliveredQtyLiters());
            entity.setCompletedAt(OffsetDateTime.now());
            entity.setCompletedBy(normalizedActor);
            applySlaOutcome(entity);

            if (!saleAlreadyRecorded) {
                SaleResponse sale = createSaleFromTask(
                        entity,
                        req.getCollectedAmount(),
                        normalizedActor,
                        overrideWithdrawalLock,
                        req.getOverrideReason(),
                        privilegedActor
                );
                entity.setSaleId(sale.getSaleId());
                entity.setSaleRecordedAt(OffsetDateTime.now());
            }
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
            applySlaOutcome(entity);
        }

        if (req.getNotes() != null) {
            entity.setNotes(trimToNull(req.getNotes()));
        }

        DeliveryTaskEntity saved = deliveryTaskRepository.save(entity);
        taskAutomationService.upsertFromDeliveryTask(saved);
        return toResponse(saved);
    }

    public UpdateDeliveryTaskStatusBulkResponse bulkUpdateStatus(
            UpdateDeliveryTaskStatusBulkRequest req,
            String actorUsername,
            boolean privilegedActor
    ) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("items are required");
        }

        List<UpdateDeliveryTaskStatusBulkItemResponse> results = new ArrayList<>();
        int successCount = 0;

        for (UpdateDeliveryTaskStatusBulkItemRequest item : req.getItems()) {
            if (item == null || trimToNull(item.getDeliveryTaskId()) == null || item.getStatus() == null) {
                results.add(
                        UpdateDeliveryTaskStatusBulkItemResponse.builder()
                                .deliveryTaskId(item != null ? trimToNull(item.getDeliveryTaskId()) : null)
                                .success(false)
                                .errorMessage("deliveryTaskId and status are required")
                                .task(null)
                                .build()
                );
                continue;
            }

            try {
                UpdateDeliveryTaskStatusRequest single = UpdateDeliveryTaskStatusRequest.builder()
                        .status(item.getStatus())
                        .deliveredQtyLiters(item.getDeliveredQtyLiters())
                        .collectedAmount(item.getCollectedAmount())
                        .overrideWithdrawalLock(item.getOverrideWithdrawalLock())
                        .overrideReason(item.getOverrideReason())
                        .notes(item.getNotes())
                        .build();

                DeliveryTaskResponse updated = updateStatus(
                        item.getDeliveryTaskId(),
                        single,
                        actorUsername,
                        privilegedActor
                );
                successCount += 1;
                results.add(
                        UpdateDeliveryTaskStatusBulkItemResponse.builder()
                                .deliveryTaskId(item.getDeliveryTaskId())
                                .success(true)
                                .errorMessage(null)
                                .task(updated)
                                .build()
                );
            } catch (Exception e) {
                String message = trimToNull(e.getMessage());
                results.add(
                        UpdateDeliveryTaskStatusBulkItemResponse.builder()
                                .deliveryTaskId(item.getDeliveryTaskId())
                                .success(false)
                                .errorMessage(message != null ? message : "Status update failed")
                                .task(null)
                                .build()
                );
            }
        }

        return UpdateDeliveryTaskStatusBulkResponse.builder()
                .totalCount(req.getItems().size())
                .successCount(successCount)
                .failedCount(req.getItems().size() - successCount)
                .items(results)
                .build();
    }

    public List<DeliveryReconciliationRowResponse> reconciliation(LocalDate date, String actorUsername, boolean privilegedActor) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String actor = normalizeActor(actorUsername);

        List<DeliveryTaskEntity> tasks = deliveryTaskRepository.findByTaskDate(effectiveDate);
        if (!privilegedActor) {
            tasks = tasks.stream()
                    .filter(task -> {
                        String assignee = trimToNull(task.getAssignedToUsername());
                        String completer = trimToNull(task.getCompletedBy());
                        return actor.equalsIgnoreCase(assignee) || actor.equalsIgnoreCase(completer);
                    })
                    .toList();
        }

        List<String> saleIds = tasks.stream()
                .map(DeliveryTaskEntity::getSaleId)
                .map(this::trimToNull)
                .filter(v -> v != null)
                .distinct()
                .toList();

        Map<String, SaleEntity> saleById = new HashMap<>();
        if (!saleIds.isEmpty()) {
            for (SaleEntity sale : saleRepository.findAllById(saleIds)) {
                saleById.put(sale.getSaleId(), sale);
            }
        }

        Map<String, DeliveryReconciliationAccumulator> accByUser = new LinkedHashMap<>();
        for (DeliveryTaskEntity task : tasks) {
            String owner = trimToNull(task.getCompletedBy());
            if (owner == null) {
                owner = trimToNull(task.getAssignedToUsername());
            }
            if (owner == null) {
                owner = "unassigned";
            }

            DeliveryReconciliationAccumulator acc = accByUser.computeIfAbsent(owner, DeliveryReconciliationAccumulator::new);
            acc.assignedTasks += 1;
            acc.plannedQty += task.getPlannedQtyLiters();

            DeliveryTaskStatus status = task.getStatus() == null ? DeliveryTaskStatus.PENDING : task.getStatus();
            switch (status) {
                case DELIVERED -> {
                    acc.deliveredTasks += 1;
                    acc.deliveredQty += task.getDeliveredQtyLiters() != null
                            ? task.getDeliveredQtyLiters()
                            : task.getPlannedQtyLiters();
                    if (Boolean.TRUE.equals(task.getSlaBreached())) {
                        acc.slaBreachedDeliveredTasks += 1;
                    } else {
                        acc.onTimeDeliveredTasks += 1;
                    }
                    acc.totalDelayMinutesForDelivered += Math.max(0, task.getSlaDelayMinutes() == null ? 0 : task.getSlaDelayMinutes());
                }
                case SKIPPED -> acc.skippedTasks += 1;
                case PENDING -> acc.pendingTasks += 1;
            }

            SaleEntity sale = saleById.get(task.getSaleId());
            if (sale != null) {
                acc.collectedAmount += safeDouble(sale.getReceivedAmount());
                acc.pendingAmount += safeDouble(sale.getPendingAmount());
            }
        }

        List<DeliveryReconciliationRowResponse> rows = new ArrayList<>();
        for (DeliveryReconciliationAccumulator acc : accByUser.values()) {
            rows.add(DeliveryReconciliationRowResponse.builder()
                    .date(effectiveDate)
                    .deliveryUsername(acc.deliveryUsername)
                    .assignedTasks(acc.assignedTasks)
                    .deliveredTasks(acc.deliveredTasks)
                    .skippedTasks(acc.skippedTasks)
                    .pendingTasks(acc.pendingTasks)
                    .plannedQty(roundTo2(acc.plannedQty))
                    .deliveredQty(roundTo2(acc.deliveredQty))
                    .collectedAmount(roundTo2(acc.collectedAmount))
                    .pendingAmount(roundTo2(acc.pendingAmount))
                    .onTimeDeliveredTasks(acc.onTimeDeliveredTasks)
                    .slaBreachedDeliveredTasks(acc.slaBreachedDeliveredTasks)
                    .avgDelayMinutesForDelivered(roundTo2(
                            acc.deliveredTasks == 0 ? 0.0 : (acc.totalDelayMinutesForDelivered / (double) acc.deliveredTasks)
                    ))
                    .build());
        }

        rows.sort(Comparator.comparing(DeliveryReconciliationRowResponse::getDeliveryUsername, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private SaleResponse createSaleFromTask(
            DeliveryTaskEntity entity,
            Double collectedAmount,
            String actorUsername,
            boolean overrideWithdrawalLock,
            String overrideReason,
            boolean privilegedActor
    ) {
        CustomerRecordEntity linkedCustomer = null;
        String normalizedCustomerId = trimToNull(entity.getCustomerId());
        if (normalizedCustomerId != null) {
            linkedCustomer = customerRecordRepository.findById(normalizedCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Linked customer not found for delivery task"));
        }

        if (overrideWithdrawalLock && !privilegedActor) {
            throw new IllegalArgumentException("Only ADMIN/MANAGER can override withdrawal lock");
        }

        ProductType productType = entity.getProductType() != null ? entity.getProductType() : ProductType.MILK;
        CustomerType customerType = linkedCustomer != null ? linkedCustomer.getCustomerType() : CustomerType.INDIVIDUAL;
        Shift passShift = productType == ProductType.MILK
                ? resolvePassShift(entity.getTaskDate(), entity.getTaskShift())
                : null;
        double quantity = entity.getDeliveredQtyLiters() != null ? entity.getDeliveredQtyLiters() : entity.getPlannedQtyLiters();
        double receivedAmount = collectedAmount == null ? 0.0 : collectedAmount;

        CreateSaleRequest saleRequest = CreateSaleRequest.builder()
                .dispatchDate(entity.getTaskDate())
                .customerType(customerType)
                .customerId(normalizedCustomerId)
                .customerName(entity.getCustomerName())
                .productType(productType)
                .quantity(quantity)
                .unitPrice(entity.getUnitPrice())
                .receivedAmount(receivedAmount)
                .paymentMode(entity.getPaymentMode() != null ? entity.getPaymentMode() : PaymentMode.CREDIT)
                .batchDate(productType == ProductType.MILK ? entity.getTaskDate() : null)
                .batchShift(passShift)
                .routeName(entity.getRouteName())
                .collectionPoint(linkedCustomer != null ? linkedCustomer.getCollectionPoint() : null)
                .overrideWithdrawalLock(overrideWithdrawalLock)
                .overrideReason(overrideWithdrawalLock ? trimToNull(overrideReason) : null)
                .notes("Auto sale from delivery task " + entity.getDeliveryTaskId())
                .build();

        return saleService.create(saleRequest, actorUsername);
    }

    private Shift resolvePassShift(LocalDate taskDate, Shift shift) {
        Shift effectiveShift = shift != null ? shift : Shift.AM;
        boolean pass = milkBatchRepository.findByDateAndShift(taskDate, effectiveShift)
                .map(batch -> batch.getQcStatus() == QcStatus.PASS)
                .orElse(false);
        if (!pass) {
            throw new IllegalArgumentException(
                    "No PASS milk batch found for " + taskDate + " " + effectiveShift + ". Complete QC first."
            );
        }
        return effectiveShift;
    }

    private void validateMilkDeliveryEligibility(DeliveryTaskEntity entity, double candidateDeliveredQty) {
        ProductType productType = entity.getProductType() != null ? entity.getProductType() : ProductType.MILK;
        if (productType != ProductType.MILK) {
            return;
        }

        LocalDate taskDate = entity.getTaskDate();
        if (taskDate == null) {
            throw new IllegalArgumentException("taskDate is required for milk delivery");
        }
        Shift shift = entity.getTaskShift() != null ? entity.getTaskShift() : Shift.AM;

        var batch = milkBatchRepository.findByDateAndShift(taskDate, shift)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No milk batch found for " + taskDate + " " + shift + ". Save batch and complete QC first."
                ));
        if (batch.getQcStatus() != QcStatus.PASS) {
            throw new IllegalArgumentException(
                    "Milk QC must be PASS for " + taskDate + " " + shift + ". Current: " + batch.getQcStatus()
            );
        }

        double alreadyDeliveredByOthers = deliveryTaskRepository.findByTaskDate(taskDate)
                .stream()
                .filter(row -> !Objects.equals(row.getDeliveryTaskId(), entity.getDeliveryTaskId()))
                .filter(row -> (row.getTaskShift() != null ? row.getTaskShift() : Shift.AM) == shift)
                .filter(row -> (row.getProductType() != null ? row.getProductType() : ProductType.MILK) == ProductType.MILK)
                .filter(row -> (row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus()) == DeliveryTaskStatus.DELIVERED)
                .mapToDouble(row -> row.getDeliveredQtyLiters() != null ? row.getDeliveredQtyLiters() : row.getPlannedQtyLiters())
                .sum();

        double availableLiters = batch.getTotalLiters();
        if (alreadyDeliveredByOthers + candidateDeliveredQty > availableLiters + 1e-6) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ROOT,
                            "Insufficient milk stock for %s %s. Batch %.2f L, already delivered %.2f L, this task %.2f L.",
                            taskDate,
                            shift,
                            availableLiters,
                            alreadyDeliveredByOthers,
                            candidateDeliveredQty
                    )
            );
        }
    }

    private String buildId() {
        return "DTK_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int upsertSubscriptionTask(
            CustomerRecordEntity customer,
            LocalDate date,
            Shift shift,
            ProductType productType,
            LocalTime preferredTime,
            double qty,
            double unitPrice,
            String actor,
            String activeDaysCsv,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (qty <= 0) {
            return 0;
        }

        ProductType safeProduct = productType != null ? productType : ProductType.MILK;
        String sourceRefId = buildSubscriptionSourceRef(customer.getCustomerId(), date, shift, safeProduct, preferredTime);
        DeliveryTaskEntity existing = deliveryTaskRepository.findBySourceRefId(sourceRefId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == DeliveryTaskStatus.PENDING) {
                existing.setCustomerName(customer.getCustomerName());
                existing.setRouteName(trimToNull(customer.getRouteName()));
                existing.setProductType(safeProduct);
                existing.setTaskShift(shift);
                existing.setPreferredTime(preferredTime);
                existing.setPlannedQtyLiters(qty);
                existing.setUnitPrice(unitPrice);
                existing.setPaymentMode(PaymentMode.CREDIT);
                existing.setAutoGenerated(true);
                existing.setSourceRefId(sourceRefId);
                existing.setNotes(buildSubscriptionNote(customer, shift, safeProduct, preferredTime, activeDaysCsv, startDate, endDate));
                existing.setCreatedBy(existing.getCreatedBy() == null ? actor : existing.getCreatedBy());
                DeliveryTaskEntity saved = deliveryTaskRepository.save(existing);
                taskAutomationService.upsertFromDeliveryTask(saved);
            }
            return 0;
        }

        DeliveryTaskEntity created = DeliveryTaskEntity.builder()
                .deliveryTaskId(buildId())
                .taskDate(date)
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .assignedToUsername(null)
                .assignedByUsername(null)
                .assignedAt(null)
                .routeName(trimToNull(customer.getRouteName()))
                .productType(safeProduct)
                .taskShift(shift)
                .preferredTime(preferredTime)
                .plannedQtyLiters(qty)
                .unitPrice(unitPrice)
                .paymentMode(PaymentMode.CREDIT)
                .deliveredQtyLiters(null)
                .saleId(null)
                .saleRecordedAt(null)
                .status(DeliveryTaskStatus.PENDING)
                .autoGenerated(true)
                .sourceRefId(sourceRefId)
                .notes(buildSubscriptionNote(customer, shift, safeProduct, preferredTime, activeDaysCsv, startDate, endDate))
                .createdBy(actor)
                .completedBy(null)
                .completedAt(null)
                .build();

        DeliveryTaskEntity saved = deliveryTaskRepository.save(created);
        taskAutomationService.upsertFromDeliveryTask(saved);
        return 1;
    }

    private int autoAssignPendingTasks(LocalDate date, String actorUsername) {
        List<String> assignableUsers = authUserRepository
                .findByActiveTrueAndRoleInOrderByUsernameAsc(List.of(UserRole.DELIVERY, UserRole.WORKER, UserRole.MANAGER))
                .stream()
                .map(AuthUserEntity::getUsername)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
        if (assignableUsers.isEmpty()) {
            return 0;
        }

        List<DeliveryTaskEntity> unassigned = deliveryTaskRepository.findByTaskDateAndStatus(date, DeliveryTaskStatus.PENDING)
                .stream()
                .filter(row -> trimToNull(row.getAssignedToUsername()) == null)
                .sorted(deliverySort())
                .toList();
        if (unassigned.isEmpty()) {
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<DeliveryTaskEntity> updates = new ArrayList<>();
        int idx = 0;
        for (DeliveryTaskEntity row : unassigned) {
            String assignee = assignableUsers.get(idx % assignableUsers.size());
            idx += 1;
            row.setAssignedToUsername(assignee);
            row.setAssignedByUsername(actorUsername);
            row.setAssignedAt(now);
            updates.add(row);
        }

        List<DeliveryTaskEntity> saved = deliveryTaskRepository.saveAll(updates);
        saved.forEach(taskAutomationService::upsertFromDeliveryTask);
        return saved.size();
    }

    private DeliveryTaskEntity findPendingMergeTarget(
            LocalDate taskDate,
            String customerId,
            String customerName,
            Shift shift,
            ProductType productType,
            LocalTime preferredTime
    ) {
        if (customerId != null) {
            return deliveryTaskRepository
                    .findByTaskDateAndCustomerIdAndTaskShiftAndProductTypeAndPreferredTimeAndStatus(
                            taskDate,
                            customerId,
                            shift,
                            productType,
                            preferredTime,
                            DeliveryTaskStatus.PENDING
                    )
                    .orElse(null);
        }

        String normalizedName = sortable(customerName);
        for (DeliveryTaskEntity row : deliveryTaskRepository.findByTaskDateAndStatus(taskDate, DeliveryTaskStatus.PENDING)) {
            if (!sortable(row.getCustomerName()).equals(normalizedName)) {
                continue;
            }
            if (row.getTaskShift() != shift) {
                continue;
            }
            if ((row.getProductType() != null ? row.getProductType() : ProductType.MILK) != productType) {
                continue;
            }
            if (!java.util.Objects.equals(row.getPreferredTime(), preferredTime)) {
                continue;
            }
            return row;
        }
        return null;
    }

    private CustomerRecordEntity resolveLinkedCustomer(String customerId, String customerName) {
        String normalizedCustomerId = trimToNull(customerId);
        String normalizedCustomerName = trimToNull(customerName);

        if (normalizedCustomerId != null) {
            return customerRecordRepository.findById(normalizedCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found for customerId: " + normalizedCustomerId));
        }
        if (normalizedCustomerName == null) {
            return null;
        }

        List<CustomerRecordEntity> matches = customerRecordRepository.findByCustomerNameIgnoreCase(normalizedCustomerName);
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple customers found for name. Pass customerId.");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private String mergeNotes(String existing, String next, String actor) {
        String appended = trimToNull(next);
        if (appended == null) {
            return trimToNull(existing);
        }
        String prefix = "[ADDON by " + actor + " " + OffsetDateTime.now().toLocalTime().withNano(0) + "] ";
        String candidate = trimToNull(existing);
        String merged = candidate == null ? prefix + appended : candidate + " | " + prefix + appended;
        return merged.length() > 500 ? merged.substring(0, 500) : merged;
    }

    private boolean shouldGenerateForDate(
            CustomerRecordEntity customer,
            LocalDate date,
            String activeDaysCsv,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return generationBlockReason(customer, date, activeDaysCsv, startDate, endDate) == null;
    }

    private String generationBlockReason(
            CustomerRecordEntity customer,
            LocalDate date,
            String activeDaysCsv,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (customer == null) {
            return "CUSTOMER_NOT_FOUND_OR_INACTIVE";
        }
        if (!customer.isActive()) {
            return "CUSTOMER_INACTIVE";
        }
        if (!customer.isSubscriptionActive()) {
            return "SUBSCRIPTION_NOT_ACTIVE";
        }
        if (startDate != null && date.isBefore(startDate)) {
            return "BEFORE_START_DATE";
        }
        if (endDate != null && date.isAfter(endDate)) {
            return "AFTER_END_DATE";
        }
        if (customer.getSubscriptionPausedUntil() != null && !date.isAfter(customer.getSubscriptionPausedUntil())) {
            return "PAUSED_UNTIL_DATE";
        }
        Set<LocalDate> skippedDates = parseSkipDates(customer.getSubscriptionSkipDatesCsv());
        if (skippedDates.contains(date)) {
            return "SKIP_DATE";
        }

        EnumSet<DayOfWeek> holidayWeekdays = parseActiveDays(customer.getSubscriptionHolidayWeekdaysCsv());
        if (!holidayWeekdays.isEmpty() && holidayWeekdays.contains(date.getDayOfWeek())) {
            return "HOLIDAY_WEEKDAY";
        }

        EnumSet<DayOfWeek> lineDays = parseActiveDays(activeDaysCsv);
        if (!lineDays.isEmpty() && !lineDays.contains(date.getDayOfWeek())) {
            return "DAY_NOT_IN_ACTIVE_DAYS";
        }

        SubscriptionFrequency frequency = customer.getSubscriptionFrequency();
        if (frequency == null || frequency == SubscriptionFrequency.DAILY) {
            return null;
        }
        if (frequency == SubscriptionFrequency.WEEKLY) {
            // Legacy customer-level frequency does not have a week-day selector.
            // Keep generation daily here; weekly cadence should be configured using
            // per-line activeDaysCsv in subscription lines.
            return null;
        }
        return null;
    }

    private boolean isExistingTaskReason(String reason) {
        String normalized = trimToNull(reason);
        if (normalized == null) {
            return false;
        }
        return REASON_TASK_ALREADY_EXISTS_PENDING.equals(normalized)
                || REASON_TASK_ALREADY_EXISTS_DELIVERED.equals(normalized)
                || REASON_TASK_ALREADY_EXISTS_SKIPPED.equals(normalized);
    }

    private String existingTaskReason(String sourceRefId) {
        String normalizedSourceRefId = trimToNull(sourceRefId);
        if (normalizedSourceRefId == null) {
            return null;
        }
        DeliveryTaskEntity existing = deliveryTaskRepository.findBySourceRefId(normalizedSourceRefId).orElse(null);
        if (existing == null) {
            return null;
        }
        DeliveryTaskStatus status = existing.getStatus() == null ? DeliveryTaskStatus.PENDING : existing.getStatus();
        return switch (status) {
            case DELIVERED -> REASON_TASK_ALREADY_EXISTS_DELIVERED;
            case SKIPPED -> REASON_TASK_ALREADY_EXISTS_SKIPPED;
            case PENDING -> REASON_TASK_ALREADY_EXISTS_PENDING;
        };
    }

    private Set<LocalDate> parseSkipDates(String csv) {
        Set<LocalDate> dates = new HashSet<>();
        String raw = trimToNull(csv);
        if (raw == null) {
            return dates;
        }
        String[] tokens = raw.split("[,\\s]+");
        for (String token : tokens) {
            String normalized = trimToNull(token);
            if (normalized == null) {
                continue;
            }
            try {
                dates.add(LocalDate.parse(normalized));
            } catch (DateTimeParseException ignore) {
                // Ignore malformed dates in stored CSV.
            }
        }
        return dates;
    }

    private EnumSet<DayOfWeek> parseActiveDays(String csv) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        String raw = trimToNull(csv);
        if (raw == null) {
            return days;
        }

        String[] tokens = raw.split("[,\\s]+");
        for (String token : tokens) {
            DayOfWeek day = parseDay(token);
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private DayOfWeek parseDay(String token) {
        String normalized = trimToNull(token);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String buildSubscriptionSourceRef(
            String customerId,
            LocalDate date,
            Shift shift,
            ProductType productType,
            LocalTime preferredTime
    ) {
        String timePart = preferredTime != null ? preferredTime.format(SOURCE_TIME) : "NA";
        return "SUBSCRIPTION:"
                + customerId
                + ":"
                + date
                + ":"
                + shift
                + ":"
                + productType
                + ":"
                + timePart;
    }

    private String buildSubscriptionNote(
            CustomerRecordEntity customer,
            Shift shift,
            ProductType productType,
            LocalTime preferredTime,
            String activeDaysCsv,
            LocalDate startDate,
            LocalDate endDate
    ) {
        SubscriptionFrequency frequency = customer.getSubscriptionFrequency() == null
                ? SubscriptionFrequency.DAILY
                : customer.getSubscriptionFrequency();
        String route = trimToNull(customer.getRouteName());
        StringBuilder note = new StringBuilder("Auto-generated subscription task (")
                .append(frequency)
                .append(", ")
                .append(shift)
                .append(", ")
                .append(productType)
                .append(")");
        if (preferredTime != null) {
            note.append(" | Time: ").append(preferredTime);
        }
        if (trimToNull(activeDaysCsv) != null) {
            note.append(" | Days: ").append(activeDaysCsv);
        }
        if (startDate != null || endDate != null) {
            note.append(" | Window: ")
                    .append(startDate != null ? startDate : "-")
                    .append(" to ")
                    .append(endDate != null ? endDate : "-");
        }
        if (customer.getSubscriptionPausedUntil() != null) {
            note.append(" | PausedUntil: ").append(customer.getSubscriptionPausedUntil());
        }
        if (trimToNull(customer.getSubscriptionSkipDatesCsv()) != null) {
            note.append(" | SkipDates: ").append(customer.getSubscriptionSkipDatesCsv());
        }
        if (trimToNull(customer.getSubscriptionHolidayWeekdaysCsv()) != null) {
            note.append(" | HolidayWeekdays: ").append(customer.getSubscriptionHolidayWeekdaysCsv());
        }
        if (route != null) {
            note.append(" | Route: ").append(route);
        }
        return note.toString();
    }

    private void optimizeRoutesIfNeeded(LocalDate date, String actorUsername) {
        List<DeliveryTaskEntity> rows = deliveryTaskRepository.findByTaskDate(date);
        boolean needsOptimization = rows.stream().anyMatch(row ->
                row.getOptimizedStopOrder() == null
                        || row.getPlannedEta() == null
                        || row.getSlaDueTime() == null
                        || ((row.getStatus() == DeliveryTaskStatus.DELIVERED)
                        && row.getCompletedAt() != null
                        && row.getSlaBreached() == null)
        );
        if (needsOptimization) {
            optimizeRoutes(date, null, null, actorUsername);
        }
    }

    private Comparator<DeliveryTaskEntity> routeOptimizationSort() {
        return Comparator
                .comparing((DeliveryTaskEntity row) -> row.getPreferredTime(), Comparator.nullsLast(LocalTime::compareTo))
                .thenComparing(row -> sortable(row.getCustomerName()))
                .thenComparing(DeliveryTaskEntity::getDeliveryTaskId);
    }

    private LocalTime routeStartTime(Shift shift) {
        Shift effectiveShift = shift != null ? shift : Shift.AM;
        return effectiveShift == Shift.PM ? PM_ROUTE_START : AM_ROUTE_START;
    }

    private void applySlaOutcome(DeliveryTaskEntity row) {
        DeliveryTaskStatus status = row.getStatus() == null ? DeliveryTaskStatus.PENDING : row.getStatus();
        if (status != DeliveryTaskStatus.DELIVERED || row.getCompletedAt() == null || row.getSlaDueTime() == null) {
            row.setSlaBreached(null);
            row.setSlaDelayMinutes(null);
            return;
        }
        long delayMinutes = Duration.between(row.getSlaDueTime(), row.getCompletedAt().toLocalTime()).toMinutes();
        boolean breached = delayMinutes > 0;
        row.setSlaBreached(breached);
        row.setSlaDelayMinutes(breached ? (int) Math.min(Integer.MAX_VALUE, delayMinutes) : 0);
    }

    private Comparator<DeliveryTaskEntity> deliverySort() {
        return Comparator.comparing((DeliveryTaskEntity row) -> sortable(row.getRouteName()))
                .thenComparing(row -> row.getOptimizedStopOrder() == null ? Integer.MAX_VALUE : row.getOptimizedStopOrder())
                .thenComparing(row -> row.getPlannedEta() != null ? row.getPlannedEta().toString() : "")
                .thenComparing(row -> row.getPreferredTime() != null ? row.getPreferredTime().toString() : "")
                .thenComparing(row -> sortable(row.getCustomerName()))
                .thenComparing(row -> (row.getProductType() != null ? row.getProductType() : ProductType.MILK).name())
                .thenComparing(row -> row.getTaskShift() != null ? row.getTaskShift().name() : "")
                .thenComparing(DeliveryTaskEntity::getDeliveryTaskId);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sortable(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private DeliveryTaskResponse toResponse(DeliveryTaskEntity entity) {
        return DeliveryTaskResponse.builder()
                .deliveryTaskId(entity.getDeliveryTaskId())
                .taskDate(entity.getTaskDate())
                .customerId(entity.getCustomerId())
                .customerName(entity.getCustomerName())
                .assignedToUsername(entity.getAssignedToUsername())
                .assignedByUsername(entity.getAssignedByUsername())
                .assignedAt(entity.getAssignedAt())
                .routeName(entity.getRouteName())
                .productType(entity.getProductType())
                .taskShift(entity.getTaskShift())
                .preferredTime(entity.getPreferredTime())
                .optimizedStopOrder(entity.getOptimizedStopOrder())
                .plannedEta(entity.getPlannedEta())
                .slaDueTime(entity.getSlaDueTime())
                .slaBreached(entity.getSlaBreached())
                .slaDelayMinutes(entity.getSlaDelayMinutes())
                .optimizedAt(entity.getOptimizedAt())
                .plannedQtyLiters(entity.getPlannedQtyLiters())
                .unitPrice(entity.getUnitPrice())
                .paymentMode(entity.getPaymentMode())
                .deliveredQtyLiters(entity.getDeliveredQtyLiters())
                .status(entity.getStatus())
                .autoGenerated(entity.isAutoGenerated())
                .sourceRefId(entity.getSourceRefId())
                .saleId(entity.getSaleId())
                .saleRecordedAt(entity.getSaleRecordedAt())
                .notes(entity.getNotes())
                .createdBy(entity.getCreatedBy())
                .completedBy(entity.getCompletedBy())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DeliveryRunClosureResponse toRunClosureResponse(
            DeliveryRunClosureEntity entity,
            StockClosureState closureState,
            boolean autoTransferTriggered,
            double autoTransferredLiters
    ) {
        String stockState = closureState.state();
        String stockMessage = closureState.message();
        if (autoTransferTriggered) {
            stockState = "AUTO_TRANSFERRED";
            stockMessage = String.format(
                    Locale.ROOT,
                    "Auto-transferred %.2f L milk to curd after both shifts closed.",
                    roundTo2(autoTransferredLiters)
            );
        }

        return DeliveryRunClosureResponse.builder()
                .runClosureId(entity.getRunClosureId())
                .date(entity.getDate())
                .routeName(entity.getRouteName())
                .shift(entity.getShift())
                .totalStops(entity.getTotalStops())
                .deliveredStops(entity.getDeliveredStops())
                .pendingStops(entity.getPendingStops())
                .skippedStops(entity.getSkippedStops())
                .expectedCollection(entity.getExpectedCollection())
                .actualCollection(entity.getActualCollection())
                .variance(entity.getVariance())
                .cashCollection(entity.getCashCollection())
                .upiCollection(entity.getUpiCollection())
                .otherCollection(entity.getOtherCollection())
                .notes(entity.getNotes())
                .closedBy(entity.getClosedBy())
                .closedAt(entity.getClosedAt())
                .amShiftClosed(closureState.amShiftClosed())
                .pmShiftClosed(closureState.pmShiftClosed())
                .bothShiftsClosed(closureState.bothShiftsClosed())
                .pendingMilkToCurdLiters(roundTo2(closureState.pendingMilkToCurdLiters()))
                .stockAutoTransferTriggered(autoTransferTriggered)
                .stockTransferState(stockState)
                .stockAlertChannel("IN_APP")
                .stockAlertMessage(stockMessage)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private StockClosureState evaluateStockClosureState(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        List<DeliveryTaskEntity> dayTasks = deliveryTaskRepository.findByTaskDate(effectiveDate);

        boolean hasAmTasks = dayTasks.stream().anyMatch(task -> effectiveShift(task) == Shift.AM);
        boolean hasPmTasks = dayTasks.stream().anyMatch(task -> effectiveShift(task) == Shift.PM);

        boolean amPending = dayTasks.stream().anyMatch(task ->
                effectiveShift(task) == Shift.AM && effectiveStatus(task) == DeliveryTaskStatus.PENDING
        );
        boolean pmPending = dayTasks.stream().anyMatch(task ->
                effectiveShift(task) == Shift.PM && effectiveStatus(task) == DeliveryTaskStatus.PENDING
        );

        boolean amShiftClosed = hasAmTasks && !amPending;
        boolean pmShiftClosed = hasPmTasks && !pmPending;
        boolean bothShiftsClosed = hasAmTasks && hasPmTasks && amShiftClosed && pmShiftClosed;

        ProcessingStockSummaryResponse stockSummary = processingStockService.summary(effectiveDate);
        double pendingMilkToCurdLiters = bothShiftsClosed
                ? Math.max(0, stockSummary.getMilkBalanceLiters())
                : 0;

        String state;
        String message;
        if (!bothShiftsClosed) {
            state = "WAITING_SHIFT_CLOSE";
            message = "AM/PM delivery shifts are not closed yet. Stock transfer alert stays pending.";
        } else if (pendingMilkToCurdLiters > 0) {
            state = "READY_FOR_AUTO_TRANSFER";
            message = String.format(
                    Locale.ROOT,
                    "Both shifts closed. Pending milk %.2f L should move to curd.",
                    roundTo2(pendingMilkToCurdLiters)
            );
        } else {
            state = "NO_PENDING_TRANSFER";
            message = "Both shifts closed and no pending milk transfer to curd.";
        }

        return new StockClosureState(
                amShiftClosed,
                pmShiftClosed,
                bothShiftsClosed,
                pendingMilkToCurdLiters,
                state,
                message
        );
    }

    private Shift effectiveShift(DeliveryTaskEntity task) {
        return task.getTaskShift() != null ? task.getTaskShift() : Shift.AM;
    }

    private DeliveryTaskStatus effectiveStatus(DeliveryTaskEntity task) {
        return task.getStatus() != null ? task.getStatus() : DeliveryTaskStatus.PENDING;
    }

    private record RouteScopeKey(String routeName, Shift shift) {
    }

    private record StockClosureState(
            boolean amShiftClosed,
            boolean pmShiftClosed,
            boolean bothShiftsClosed,
            double pendingMilkToCurdLiters,
            String state,
            String message
    ) {
    }

    private static class DeliveryReconciliationAccumulator {
        private final String deliveryUsername;
        private long assignedTasks;
        private long deliveredTasks;
        private long skippedTasks;
        private long pendingTasks;
        private long onTimeDeliveredTasks;
        private long slaBreachedDeliveredTasks;
        private double plannedQty;
        private double deliveredQty;
        private double collectedAmount;
        private double pendingAmount;
        private double totalDelayMinutesForDelivered;

        private DeliveryReconciliationAccumulator(String deliveryUsername) {
            this.deliveryUsername = deliveryUsername;
        }
    }
}
