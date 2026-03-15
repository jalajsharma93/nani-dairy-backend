package net.nani.dairy.sales;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.animals.AnimalEntity;
import net.nani.dairy.animals.AnimalRepository;
import net.nani.dairy.health.MedicalTreatmentRepository;
import net.nani.dairy.milk.MilkBatchRepository;
import net.nani.dairy.milk.MilkEntryRepository;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.dto.CustomerLedgerRowResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionInvoiceLineItemResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionInvoiceResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionInvoiceSummaryResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionStatementDailyRowResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionStatementResponse;
import net.nani.dairy.sales.dto.CustomerSubscriptionStatementSummaryResponse;
import net.nani.dairy.sales.dto.CreateSaleRequest;
import net.nani.dairy.sales.dto.DeliveryChecklistItemResponse;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkItemRequest;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkItemResponse;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkRequest;
import net.nani.dairy.sales.dto.MonthCloseSettlementBulkResponse;
import net.nani.dairy.sales.dto.MonthCloseSettlementRequest;
import net.nani.dairy.sales.dto.MonthCloseSettlementResponse;
import net.nani.dairy.sales.dto.ReconcileSaleRequest;
import net.nani.dairy.sales.dto.SaleComplianceOverrideAuditResponse;
import net.nani.dairy.sales.dto.SaleResponse;
import net.nani.dairy.sales.dto.SettlementReconciliationRowResponse;
import net.nani.dairy.sales.dto.SalesSummaryResponse;
import net.nani.dairy.sales.dto.SubscriptionInvoiceStatusAuditResponse;
import net.nani.dairy.sales.dto.SubscriptionInvoiceStatusUpdateResponse;
import net.nani.dairy.sales.dto.UpdateSubscriptionInvoiceStatusRequest;
import net.nani.dairy.sales.dto.UpdateSaleDeliveryRequest;
import net.nani.dairy.sales.dto.UpdateSaleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
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
    private final CustomerRecordRepository customerRecordRepository;
    private final CustomerSubscriptionLineRepository subscriptionLineRepository;
    private final CustomerSubscriptionInvoiceRepository subscriptionInvoiceRepository;
    private final SaleComplianceOverrideAuditRepository saleComplianceOverrideAuditRepository;
    private final SubscriptionInvoiceStatusAuditRepository subscriptionInvoiceStatusAuditRepository;
    private final TransactionTemplate transactionTemplate;

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

    @Transactional
    public SaleResponse create(CreateSaleRequest req, String actorUsername) {
        String saleId = buildId();
        CustomerRecordEntity linkedCustomer = resolveLinkedCustomer(req.getCustomerId(), req.getCustomerName());
        String normalizedCustomerName = linkedCustomer != null
                ? linkedCustomer.getCustomerName()
                : req.getCustomerName().trim();
        CustomerType normalizedCustomerType = linkedCustomer != null
                ? linkedCustomer.getCustomerType()
                : req.getCustomerType();

        validateMilkRule(
                req.getProductType(),
                req.getBatchDate(),
                req.getBatchShift(),
                req.getOverrideWithdrawalLock(),
                req.getOverrideReason(),
                saleId,
                req.getDispatchDate(),
                normalizedCustomerName,
                actorUsername,
                "CREATE"
        );

        SettlementPricing pricing = resolveSettlementPricing(
                req.getProductType(),
                normalizedCustomerType,
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
                .customerType(normalizedCustomerType)
                .customerId(linkedCustomer != null ? linkedCustomer.getCustomerId() : trimToNull(req.getCustomerId()))
                .customerName(normalizedCustomerName)
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
                .subscriptionChargeApplied(false)
                .subscriptionBalanceImpact(0.0)
                .customerBalanceAfterSale(linkedCustomer != null ? safeDouble(linkedCustomer.getRunningBalance()) : null)
                .paymentStatus(payment.paymentStatus)
                .paymentMode(req.getPaymentMode())
                .batchDate(req.getBatchDate())
                .batchShift(req.getBatchShift())
                .notes(trimToNull(req.getNotes()))
                .build();

        applySubscriptionBalanceImpact(entity, linkedCustomer);
        return toResponse(saleRepository.save(entity));
    }

    @Transactional
    public SaleResponse update(String saleId, UpdateSaleRequest req, String actorUsername) {
        CustomerRecordEntity linkedCustomer = resolveLinkedCustomer(req.getCustomerId(), req.getCustomerName());
        String normalizedCustomerName = linkedCustomer != null
                ? linkedCustomer.getCustomerName()
                : req.getCustomerName().trim();
        CustomerType normalizedCustomerType = linkedCustomer != null
                ? linkedCustomer.getCustomerType()
                : req.getCustomerType();

        validateMilkRule(
                req.getProductType(),
                req.getBatchDate(),
                req.getBatchShift(),
                req.getOverrideWithdrawalLock(),
                req.getOverrideReason(),
                saleId,
                req.getDispatchDate(),
                normalizedCustomerName,
                actorUsername,
                "UPDATE"
        );

        SaleEntity entity = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        String previousCustomerId = trimToNull(entity.getCustomerId());
        boolean previousChargeApplied = Boolean.TRUE.equals(entity.getSubscriptionChargeApplied());
        double previousBalanceImpact = safeDouble(entity.getSubscriptionBalanceImpact());

        SettlementPricing pricing = resolveSettlementPricing(
                req.getProductType(),
                normalizedCustomerType,
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
        entity.setCustomerType(normalizedCustomerType);
        entity.setCustomerId(linkedCustomer != null ? linkedCustomer.getCustomerId() : trimToNull(req.getCustomerId()));
        entity.setCustomerName(normalizedCustomerName);
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

        if (previousChargeApplied && previousCustomerId != null && previousBalanceImpact > 0) {
            CustomerRecordEntity previousCustomer = customerRecordRepository.findById(previousCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Linked customer not found for existing sale"));
            adjustCustomerRunningBalance(previousCustomer, -previousBalanceImpact);
        }

        entity.setSubscriptionChargeApplied(false);
        entity.setSubscriptionBalanceImpact(0.0);
        entity.setCustomerBalanceAfterSale(linkedCustomer != null ? safeDouble(linkedCustomer.getRunningBalance()) : null);
        applySubscriptionBalanceImpact(entity, linkedCustomer);

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

    public CustomerSubscriptionStatementResponse subscriptionStatement(
            String customerId,
            String monthText,
            boolean includeDaily
    ) {
        String normalizedCustomerId = trimToNull(customerId);
        if (normalizedCustomerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }

        YearMonth month = parseYearMonth(monthText);

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        CustomerRecordEntity customer = customerRecordRepository.findById(normalizedCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        List<CustomerSubscriptionLineEntity> lines = customerSubscriptionLinesForStatement(customer.getCustomerId());
        boolean lineBasedPricing = !lines.isEmpty();
        String pricingMode = lineBasedPricing ? "LINE_BASED" : "LEGACY_DAILY";

        Set<LocalDate> skipDates = parseSkipDates(customer.getSubscriptionSkipDatesCsv());
        LocalDate pausedUntil = customer.getSubscriptionPausedUntil();
        EnumSet<DayOfWeek> holidayWeekdays = parseActiveDays(customer.getSubscriptionHolidayWeekdaysCsv());

        List<SaleEntity> billedRows = saleRepository.findByDispatchDateBetweenAndCustomerId(from, to, customer.getCustomerId());
        if (billedRows.isEmpty()) {
            billedRows = saleRepository.findByDispatchDateBetweenAndCustomerTypeAndCustomerNameIgnoreCase(
                    from,
                    to,
                    customer.getCustomerType(),
                    customer.getCustomerName()
            );
        }

        double billedQty = 0.0;
        double billedAmount = 0.0;
        double receivedAmount = 0.0;
        double pendingAmount = 0.0;
        LinkedHashSet<LocalDate> billedDates = new LinkedHashSet<>();
        Map<LocalDate, double[]> billedByDate = new LinkedHashMap<>();
        for (SaleEntity row : billedRows) {
            billedQty += row.getQuantity();
            billedAmount += row.getTotalAmount();
            receivedAmount += row.getReceivedAmount();
            pendingAmount += row.getPendingAmount();
            billedDates.add(row.getDispatchDate());

            double[] bucket = billedByDate.computeIfAbsent(row.getDispatchDate(), ignored -> new double[]{0.0, 0.0});
            bucket[0] += row.getQuantity();
            bucket[1] += row.getTotalAmount();
        }

        double baselineQty = 0.0;
        double baselineAmount = 0.0;
        int baselinePlanDays = 0;
        double plannedQty = 0.0;
        double plannedAmount = 0.0;
        int activePlanDays = 0;
        int pausedDays = 0;
        int skipDays = 0;
        int holidayWeekdayDays = 0;

        List<CustomerSubscriptionStatementDailyRowResponse> dailyRows = includeDaily ? new ArrayList<>() : List.of();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayPlan baselinePlan = computeDayPlan(customer, lines, date, false, false);
            boolean holidayWeekday = holidayWeekdays.contains(date.getDayOfWeek());
            DayPlan effectivePlan = computeDayPlan(
                    customer,
                    lines,
                    date,
                    pausedUntil != null && !date.isAfter(pausedUntil),
                    skipDates.contains(date) || holidayWeekday
            );

            if (baselinePlan.amount > 0) {
                baselinePlanDays += 1;
                baselineQty += baselinePlan.qty;
                baselineAmount += baselinePlan.amount;
            }
            if (effectivePlan.amount > 0) {
                activePlanDays += 1;
                plannedQty += effectivePlan.qty;
                plannedAmount += effectivePlan.amount;
            } else if (baselinePlan.amount > 0 && pausedUntil != null && !date.isAfter(pausedUntil)) {
                pausedDays += 1;
            } else if (baselinePlan.amount > 0 && holidayWeekday) {
                holidayWeekdayDays += 1;
            } else if (baselinePlan.amount > 0 && skipDates.contains(date)) {
                skipDays += 1;
            }

            if (includeDaily) {
                double[] billedBucket = billedByDate.get(date);
                double billedQtyForDate = billedBucket == null ? 0.0 : billedBucket[0];
                double billedAmountForDate = billedBucket == null ? 0.0 : billedBucket[1];

                String status;
                if (effectivePlan.paused) {
                    status = "PAUSED";
                } else if (holidayWeekday) {
                    status = "HOLIDAY_WEEKDAY";
                } else if (effectivePlan.skipped) {
                    status = "HOLIDAY_SKIP";
                } else if (effectivePlan.amount > 0) {
                    status = "PLANNED";
                } else if (billedAmountForDate > 0) {
                    status = "UNPLANNED_BILLED";
                } else {
                    status = "NO_PLAN";
                }

                double expectedQty = roundTo2(effectivePlan.qty);
                double expectedAmount = roundTo2(effectivePlan.amount);
                double billedQtyRounded = roundTo2(billedQtyForDate);
                double billedAmountRounded = roundTo2(billedAmountForDate);
                dailyRows.add(
                        CustomerSubscriptionStatementDailyRowResponse.builder()
                                .date(date)
                                .dayOfWeek(date.getDayOfWeek().name())
                                .status(status)
                                .expectedQty(expectedQty)
                                .expectedAmount(expectedAmount)
                                .billedQty(billedQtyRounded)
                                .billedAmount(billedAmountRounded)
                                .varianceQty(roundTo2(billedQtyRounded - expectedQty))
                                .varianceAmount(roundTo2(billedAmountRounded - expectedAmount))
                                .build()
                );
            }
        }

        int cycleDays = month.lengthOfMonth();
        double prorationFactor = baselinePlanDays == 0 ? 0.0 : ((double) activePlanDays / (double) baselinePlanDays);
        double holidayCredit = Math.max(0, baselineAmount - plannedAmount);
        double variance = billedAmount - plannedAmount;

        return CustomerSubscriptionStatementResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .month(month.toString())
                .dateFrom(from)
                .dateTo(to)
                .subscriptionActive(customer.isSubscriptionActive())
                .pricingMode(pricingMode)
                .cycleDays(cycleDays)
                .baselinePlanDays(baselinePlanDays)
                .activePlanDays(activePlanDays)
                .pausedDays(pausedDays)
                .skipDays(skipDays)
                .holidayWeekdayDays(holidayWeekdayDays)
                .billedDays(billedDates.size())
                .prorationFactor(roundTo4(prorationFactor))
                .baselinePlanQty(roundTo2(baselineQty))
                .baselinePlanAmount(roundTo2(baselineAmount))
                .plannedQty(roundTo2(plannedQty))
                .plannedAmount(roundTo2(plannedAmount))
                .holidayCreditAmount(roundTo2(holidayCredit))
                .billedQty(roundTo2(billedQty))
                .billedAmount(roundTo2(billedAmount))
                .receivedAmount(roundTo2(receivedAmount))
                .pendingAmount(roundTo2(pendingAmount))
                .expectedVsBilledVariance(roundTo2(variance))
                .currentRunningBalance(roundTo2(safeDouble(customer.getRunningBalance())))
                .totalPaidToDate(roundTo2(safeDouble(customer.getTotalPaid())))
                .dailyRows(dailyRows)
                .build();
    }

    public List<CustomerSubscriptionStatementSummaryResponse> subscriptionStatements(
            String monthText,
            CustomerType customerType
    ) {
        YearMonth month = parseYearMonth(monthText);
        List<CustomerRecordEntity> customers = customerRecordRepository.findByIsActiveAndSubscriptionActive(true, true);
        return customers.stream()
                .filter(customer -> customerType == null || customer.getCustomerType() == customerType)
                .sorted(
                        Comparator.comparing((CustomerRecordEntity c) -> sortable(c.getRouteName()))
                                .thenComparing(c -> sortable(c.getCustomerName()))
                )
                .map(customer -> {
                    CustomerSubscriptionStatementResponse statement = subscriptionStatement(
                            customer.getCustomerId(),
                            month.toString(),
                            false
                    );
                    return CustomerSubscriptionStatementSummaryResponse.builder()
                            .customerId(customer.getCustomerId())
                            .customerName(customer.getCustomerName())
                            .customerType(customer.getCustomerType())
                            .routeName(customer.getRouteName())
                            .month(month.toString())
                            .pricingMode(statement.getPricingMode())
                            .prorationFactor(statement.getProrationFactor())
                            .activePlanDays(statement.getActivePlanDays())
                            .pausedDays(statement.getPausedDays())
                            .skipDays(statement.getSkipDays())
                            .holidayWeekdayDays(statement.getHolidayWeekdayDays())
                            .billedDays(statement.getBilledDays())
                            .plannedAmount(statement.getPlannedAmount())
                            .billedAmount(statement.getBilledAmount())
                            .receivedAmount(statement.getReceivedAmount())
                            .pendingAmount(statement.getPendingAmount())
                            .expectedVsBilledVariance(statement.getExpectedVsBilledVariance())
                            .currentRunningBalance(statement.getCurrentRunningBalance())
                            .build();
                })
                .toList();
    }

    public String subscriptionStatementsCsv(
            String monthText,
            CustomerType customerType
    ) {
        YearMonth month = parseYearMonth(monthText);
        List<CustomerSubscriptionStatementSummaryResponse> rows = subscriptionStatements(month.toString(), customerType);

        StringBuilder csv = new StringBuilder();
        csv.append("month,customer_id,customer_name,customer_type,route_name,pricing_mode,proration_factor,active_plan_days,paused_days,skip_days,holiday_weekday_days,billed_days,planned_amount,billed_amount,received_amount,pending_amount,expected_vs_billed_variance,current_running_balance\n");

        for (CustomerSubscriptionStatementSummaryResponse row : rows) {
            csv.append(csvValue(row.getMonth())).append(",")
                    .append(csvValue(row.getCustomerId())).append(",")
                    .append(csvValue(row.getCustomerName())).append(",")
                    .append(csvValue(row.getCustomerType() == null ? null : row.getCustomerType().name())).append(",")
                    .append(csvValue(row.getRouteName())).append(",")
                    .append(csvValue(row.getPricingMode())).append(",")
                    .append(roundTo4(row.getProrationFactor())).append(",")
                    .append(row.getActivePlanDays()).append(",")
                    .append(row.getPausedDays()).append(",")
                    .append(row.getSkipDays()).append(",")
                    .append(row.getHolidayWeekdayDays()).append(",")
                    .append(row.getBilledDays()).append(",")
                    .append(roundTo2(row.getPlannedAmount())).append(",")
                    .append(roundTo2(row.getBilledAmount())).append(",")
                    .append(roundTo2(row.getReceivedAmount())).append(",")
                    .append(roundTo2(row.getPendingAmount())).append(",")
                    .append(roundTo2(row.getExpectedVsBilledVariance())).append(",")
                    .append(roundTo2(row.getCurrentRunningBalance()))
                    .append("\n");
        }

        return csv.toString();
    }

    public CustomerSubscriptionInvoiceResponse subscriptionInvoice(
            String customerId,
            String monthText,
            boolean includeDaily
    ) {
        String normalizedCustomerId = trimToNull(customerId);
        if (normalizedCustomerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }

        YearMonth month = parseYearMonth(monthText);
        CustomerRecordEntity customer = customerRecordRepository.findById(normalizedCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        String monthKey = month.toString();
        CustomerSubscriptionInvoiceEntity existing = subscriptionInvoiceRepository
                .findByCustomerIdAndInvoiceMonth(normalizedCustomerId, monthKey)
                .orElse(null);

        if (existing != null && existing.getStatus() != SubscriptionInvoiceStatus.DRAFT) {
            return toSubscriptionInvoiceResponse(existing, List.of());
        }

        CustomerSubscriptionInvoiceResponse computed = buildComputedSubscriptionInvoice(customer, month, includeDaily);
        CustomerSubscriptionInvoiceEntity entity = upsertDraftSubscriptionInvoice(customer, month, computed, existing);
        return toSubscriptionInvoiceResponse(entity, computed.getDailyRows());
    }

    public List<CustomerSubscriptionInvoiceSummaryResponse> subscriptionInvoices(
            String monthText,
            CustomerType customerType
    ) {
        YearMonth month = parseYearMonth(monthText);
        List<CustomerRecordEntity> customers = customerRecordRepository.findByIsActiveAndSubscriptionActive(true, true);
        return customers.stream()
                .filter(customer -> customerType == null || customer.getCustomerType() == customerType)
                .sorted(
                        Comparator.comparing((CustomerRecordEntity c) -> sortable(c.getRouteName()))
                                .thenComparing(c -> sortable(c.getCustomerName()))
                )
                .map(customer -> toSubscriptionInvoiceSummary(subscriptionInvoice(customer.getCustomerId(), month.toString(), false)))
                .toList();
    }

    public String subscriptionInvoicesCsv(
            String monthText,
            CustomerType customerType
    ) {
        YearMonth month = parseYearMonth(monthText);
        List<CustomerSubscriptionInvoiceSummaryResponse> rows = subscriptionInvoices(month.toString(), customerType);

        StringBuilder csv = new StringBuilder();
        csv.append("month,customer_id,customer_name,customer_type,route_name,invoice_number,status,last_status_updated_at,issue_date,due_date,planned_amount,holiday_credit_amount,billed_amount,received_amount,pending_amount,opening_pending_amount,closing_pending_amount,add_on_billed_amount,under_delivery_credit_amount,proration_factor\n");

        for (CustomerSubscriptionInvoiceSummaryResponse row : rows) {
            csv.append(csvValue(row.getMonth())).append(",")
                    .append(csvValue(row.getCustomerId())).append(",")
                    .append(csvValue(row.getCustomerName())).append(",")
                    .append(csvValue(row.getCustomerType() == null ? null : row.getCustomerType().name())).append(",")
                    .append(csvValue(row.getRouteName())).append(",")
                    .append(csvValue(row.getInvoiceNumber())).append(",")
                    .append(csvValue(row.getStatus() == null ? null : row.getStatus().name())).append(",")
                    .append(csvValue(row.getLastStatusUpdatedAt() == null ? null : row.getLastStatusUpdatedAt().toString())).append(",")
                    .append(csvValue(row.getIssueDate() == null ? null : row.getIssueDate().toString())).append(",")
                    .append(csvValue(row.getDueDate() == null ? null : row.getDueDate().toString())).append(",")
                    .append(roundTo2(row.getPlannedAmount())).append(",")
                    .append(roundTo2(row.getHolidayCreditAmount())).append(",")
                    .append(roundTo2(row.getBilledAmount())).append(",")
                    .append(roundTo2(row.getReceivedAmount())).append(",")
                    .append(roundTo2(row.getPendingAmount())).append(",")
                    .append(roundTo2(row.getOpeningPendingAmount())).append(",")
                    .append(roundTo2(row.getClosingPendingAmount())).append(",")
                    .append(roundTo2(row.getAddOnBilledAmount())).append(",")
                    .append(roundTo2(row.getUnderDeliveryCreditAmount())).append(",")
                    .append(roundTo4(row.getProrationFactor()))
                    .append("\n");
        }

        return csv.toString();
    }

    public List<SubscriptionInvoiceStatusAuditResponse> subscriptionInvoiceStatusAudits(
            String monthText,
            String customerId
    ) {
        YearMonth month = parseYearMonth(monthText);
        String normalizedCustomerId = trimToNull(customerId);
        List<SubscriptionInvoiceStatusAuditEntity> rows = normalizedCustomerId != null
                ? subscriptionInvoiceStatusAuditRepository.findByInvoiceMonthAndCustomerIdOrderByCreatedAtDesc(
                month.toString(),
                normalizedCustomerId
        )
                : subscriptionInvoiceStatusAuditRepository.findByInvoiceMonthOrderByCreatedAtDesc(month.toString());

        return rows.stream().map(this::toSubscriptionInvoiceStatusAuditResponse).toList();
    }

    @Transactional
    public SubscriptionInvoiceStatusUpdateResponse finalizeSubscriptionInvoice(
            UpdateSubscriptionInvoiceStatusRequest req,
            String actorUsername
    ) {
        String customerId = normalizeRequired(req.getCustomerId(), "customerId is required");
        YearMonth month = parseYearMonth(req.getMonth());
        String actor = normalizeActor(actorUsername);

        // Ensure draft snapshot is available/up-to-date before transition.
        subscriptionInvoice(customerId, month.toString(), false);

        CustomerSubscriptionInvoiceEntity entity = subscriptionInvoiceRepository
                .findByCustomerIdAndInvoiceMonth(customerId, month.toString())
                .orElseThrow(() -> new IllegalArgumentException("Subscription invoice not found"));

        SubscriptionInvoiceStatus previous = entity.getStatus();
        if (previous == SubscriptionInvoiceStatus.POSTED) {
            throw new IllegalArgumentException("Posted invoice cannot be finalized again");
        }
        if (previous == SubscriptionInvoiceStatus.FINALIZED) {
            return toStatusUpdateResponse(entity, previous, actor);
        }

        OffsetDateTime now = OffsetDateTime.now();
        entity.setStatus(SubscriptionInvoiceStatus.FINALIZED);
        entity.setStatusNote(trimToNull(req.getNote()));
        entity.setLastStatusUpdatedAt(now);
        entity.setLastStatusUpdatedBy(actor);
        entity.setFinalizedAt(now);
        entity.setFinalizedBy(actor);
        subscriptionInvoiceRepository.save(entity);
        recordSubscriptionInvoiceStatusAudit(
                entity,
                previous,
                "FINALIZE",
                actor,
                trimToNull(req.getNote()),
                null,
                false,
                null
        );
        return toStatusUpdateResponse(entity, previous, actor);
    }

    @Transactional
    public SubscriptionInvoiceStatusUpdateResponse postSubscriptionInvoice(
            UpdateSubscriptionInvoiceStatusRequest req,
            String actorUsername
    ) {
        String customerId = normalizeRequired(req.getCustomerId(), "customerId is required");
        YearMonth month = parseYearMonth(req.getMonth());
        String actor = normalizeActor(actorUsername);

        CustomerSubscriptionInvoiceEntity entity = subscriptionInvoiceRepository
                .findByCustomerIdAndInvoiceMonth(customerId, month.toString())
                .orElseThrow(() -> new IllegalArgumentException("Subscription invoice not found"));

        SubscriptionInvoiceStatus previous = entity.getStatus();
        if (previous == SubscriptionInvoiceStatus.POSTED) {
            return toStatusUpdateResponse(entity, previous, actor);
        }
        if (previous != SubscriptionInvoiceStatus.FINALIZED) {
            throw new IllegalArgumentException("Only FINALIZED invoice can be posted");
        }

        OffsetDateTime now = OffsetDateTime.now();
        entity.setStatus(SubscriptionInvoiceStatus.POSTED);
        entity.setStatusNote(trimToNull(req.getNote()));
        entity.setLastStatusUpdatedAt(now);
        entity.setLastStatusUpdatedBy(actor);
        entity.setPostedAt(now);
        entity.setPostedBy(actor);
        subscriptionInvoiceRepository.save(entity);
        recordSubscriptionInvoiceStatusAudit(
                entity,
                previous,
                "POST",
                actor,
                trimToNull(req.getNote()),
                null,
                false,
                null
        );
        return toStatusUpdateResponse(entity, previous, actor);
    }

    @Transactional
    public SubscriptionInvoiceStatusUpdateResponse reopenSubscriptionInvoice(
            UpdateSubscriptionInvoiceStatusRequest req,
            String actorUsername
    ) {
        String customerId = normalizeRequired(req.getCustomerId(), "customerId is required");
        YearMonth month = parseYearMonth(req.getMonth());
        String actor = normalizeActor(actorUsername);
        String reason = trimToNull(req.getOverrideReason());
        if (reason == null) {
            throw new IllegalArgumentException("overrideReason is required to reopen invoice");
        }

        CustomerSubscriptionInvoiceEntity entity = subscriptionInvoiceRepository
                .findByCustomerIdAndInvoiceMonth(customerId, month.toString())
                .orElseThrow(() -> new IllegalArgumentException("Subscription invoice not found"));

        SubscriptionInvoiceStatus previous = entity.getStatus();
        if (previous == SubscriptionInvoiceStatus.DRAFT) {
            return toStatusUpdateResponse(entity, previous, actor);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String statusNote = "Reopened with override: " + reason + appendNoteSuffix(req.getNote());
        entity.setStatus(SubscriptionInvoiceStatus.DRAFT);
        entity.setStatusNote(statusNote);
        entity.setLastStatusUpdatedAt(now);
        entity.setLastStatusUpdatedBy(actor);
        entity.setPostedAt(null);
        entity.setPostedBy(null);
        entity.setFinalizedAt(null);
        entity.setFinalizedBy(null);
        subscriptionInvoiceRepository.save(entity);
        recordSubscriptionInvoiceStatusAudit(
                entity,
                previous,
                "REOPEN",
                actor,
                statusNote,
                reason,
                true,
                actor
        );
        return toStatusUpdateResponse(entity, previous, actor);
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
        assertExpectedUpdatedAt(entity, req.getExpectedUpdatedAt());

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

    @Transactional
    public MonthCloseSettlementResponse monthCloseSettlement(MonthCloseSettlementRequest req, String actorUsername) {
        return monthCloseSettlementInternal(req, actorUsername, OffsetDateTime.now());
    }

    public MonthCloseSettlementBulkResponse monthCloseSettlementBulk(
            MonthCloseSettlementBulkRequest req,
            String actorUsername
    ) {
        validateMonthCloseBulkRequest(req);

        String actor = normalizeActor(actorUsername);
        String bulkNote = trimToNull(req.getNote());
        OffsetDateTime processedAt = OffsetDateTime.now();

        List<MonthCloseSettlementBulkItemResponse> results = new ArrayList<>();
        int succeeded = 0;

        for (MonthCloseSettlementBulkItemRequest item : req.getItems()) {
            if (item == null) {
                results.add(
                        MonthCloseSettlementBulkItemResponse.builder()
                                .success(false)
                                .message("Invalid item: null")
                                .build()
                );
                continue;
            }

            MonthCloseSettlementRequest itemRequest = MonthCloseSettlementRequest.builder()
                    .dateFrom(req.getDateFrom())
                    .dateTo(req.getDateTo())
                    .customerType(item.getCustomerType())
                    .customerId(item.getCustomerId())
                    .customerName(item.getCustomerName())
                    .payoutAmount(item.getPayoutAmount())
                    .reconcileOpenCooperative(item.getReconcileOpenCooperative())
                    .note(bulkNote)
                    .build();

            try {
                MonthCloseSettlementResponse closeResult = transactionTemplate.execute(
                        status -> monthCloseSettlementInternal(itemRequest, actor, OffsetDateTime.now())
                );
                if (closeResult == null) {
                    throw new IllegalStateException("month close returned empty response");
                }
                results.add(
                        MonthCloseSettlementBulkItemResponse.builder()
                                .customerType(closeResult.getCustomerType())
                                .customerId(closeResult.getCustomerId())
                                .customerName(closeResult.getCustomerName())
                                .success(true)
                                .message("OK")
                                .reconciliationApplied(closeResult.isReconciliationApplied())
                                .reconciledSales(closeResult.getReconciledSales())
                                .payoutRecorded(closeResult.getPayoutRecorded())
                                .customerBalanceAfter(closeResult.getCustomerBalanceAfter())
                                .build()
                );
                succeeded += 1;
            } catch (Exception e) {
                String message = trimToNull(e.getMessage());
                if (message == null) {
                    message = "Month close failed";
                }
                if (message.length() > 300) {
                    message = message.substring(0, 300);
                }
                results.add(
                        MonthCloseSettlementBulkItemResponse.builder()
                                .customerType(item.getCustomerType())
                                .customerId(trimToNull(item.getCustomerId()))
                                .customerName(trimToNull(item.getCustomerName()))
                                .success(false)
                                .message(message)
                                .reconciliationApplied(false)
                                .reconciledSales(0)
                                .payoutRecorded(0.0)
                                .customerBalanceAfter(null)
                                .build()
                );
            }
        }

        return MonthCloseSettlementBulkResponse.builder()
                .dateFrom(req.getDateFrom())
                .dateTo(req.getDateTo())
                .requestedCount(req.getItems().size())
                .succeededCount(succeeded)
                .failedCount(req.getItems().size() - succeeded)
                .processedBy(actor)
                .processedAt(processedAt)
                .note(bulkNote)
                .results(results)
                .build();
    }

    public MonthCloseSettlementBulkResponse monthCloseSettlementBulkPreview(
            MonthCloseSettlementBulkRequest req,
            String actorUsername
    ) {
        validateMonthCloseBulkRequest(req);

        String actor = normalizeActor(actorUsername);
        String bulkNote = trimToNull(req.getNote());
        OffsetDateTime processedAt = OffsetDateTime.now();

        List<MonthCloseSettlementBulkItemResponse> results = new ArrayList<>();
        int succeeded = 0;

        for (MonthCloseSettlementBulkItemRequest item : req.getItems()) {
            if (item == null) {
                results.add(
                        MonthCloseSettlementBulkItemResponse.builder()
                                .success(false)
                                .message("Invalid item: null")
                                .build()
                );
                continue;
            }
            try {
                MonthCloseSettlementBulkItemResponse preview = previewMonthCloseBulkItem(req, item);
                results.add(preview);
                if (preview.isSuccess()) {
                    succeeded += 1;
                }
            } catch (Exception e) {
                String message = trimToNull(e.getMessage());
                if (message == null) {
                    message = "Month close preview failed";
                }
                if (message.length() > 300) {
                    message = message.substring(0, 300);
                }
                results.add(
                        MonthCloseSettlementBulkItemResponse.builder()
                                .customerType(item.getCustomerType())
                                .customerId(trimToNull(item.getCustomerId()))
                                .customerName(trimToNull(item.getCustomerName()))
                                .success(false)
                                .message(message)
                                .reconciliationApplied(false)
                                .reconciledSales(0)
                                .payoutRecorded(0.0)
                                .customerBalanceAfter(null)
                                .build()
                );
            }
        }

        return MonthCloseSettlementBulkResponse.builder()
                .dateFrom(req.getDateFrom())
                .dateTo(req.getDateTo())
                .requestedCount(req.getItems().size())
                .succeededCount(succeeded)
                .failedCount(req.getItems().size() - succeeded)
                .processedBy(actor)
                .processedAt(processedAt)
                .note(bulkNote)
                .results(results)
                .build();
    }

    private MonthCloseSettlementResponse monthCloseSettlementInternal(
            MonthCloseSettlementRequest req,
            String actorUsername,
            OffsetDateTime closedAt
    ) {
        if (req.getDateFrom() == null || req.getDateTo() == null) {
            throw new IllegalArgumentException("dateFrom and dateTo are required");
        }
        if (req.getDateTo().isBefore(req.getDateFrom())) {
            throw new IllegalArgumentException("dateTo cannot be before dateFrom");
        }
        if (req.getCustomerType() == null) {
            throw new IllegalArgumentException("customerType is required");
        }

        String normalizedCustomerName = trimToNull(req.getCustomerName());
        if (normalizedCustomerName == null) {
            throw new IllegalArgumentException("customerName is required");
        }

        double payoutAmount = req.getPayoutAmount() == null ? 0.0 : req.getPayoutAmount();
        if (payoutAmount < 0) {
            throw new IllegalArgumentException("payoutAmount cannot be negative");
        }

        String normalizedCustomerId = trimToNull(req.getCustomerId());
        String actor = normalizeActor(actorUsername);
        String note = trimToNull(req.getNote());

        boolean reconciliationApplied =
                req.getCustomerType() == CustomerType.COOPERATIVE
                        && Boolean.TRUE.equals(req.getReconcileOpenCooperative());
        int reconciledSales = 0;

        if (reconciliationApplied) {
            List<SaleEntity> openRows =
                    saleRepository.findByDispatchDateBetweenAndCustomerTypeAndCustomerNameIgnoreCaseAndProductTypeAndReconciledFalse(
                            req.getDateFrom(),
                            req.getDateTo(),
                            req.getCustomerType(),
                            normalizedCustomerName,
                            ProductType.MILK
                    );

            String defaultNote = "Month close " + req.getDateFrom() + " to " + req.getDateTo();
            for (SaleEntity row : openRows) {
                row.setReconciled(true);
                row.setReconciledAt(closedAt);
                row.setReconciledBy(actor);
                if (note != null) {
                    row.setReconciliationNote(note);
                } else if (trimToNull(row.getReconciliationNote()) == null) {
                    row.setReconciliationNote(defaultNote);
                }
            }
            if (!openRows.isEmpty()) {
                saleRepository.saveAll(openRows);
            }
            reconciledSales = openRows.size();
        }

        CustomerRecordEntity customer = resolveCustomerForMonthClose(
                normalizedCustomerId,
                normalizedCustomerName,
                req.getCustomerType()
        );

        double payoutRecorded = 0;
        Double customerBalanceAfter = customer != null ? safeDouble(customer.getRunningBalance()) : null;
        if (payoutAmount > 0) {
            if (customer == null) {
                throw new IllegalArgumentException("Customer record not found for payout");
            }

            double currentBalance = safeDouble(customer.getRunningBalance());
            if (payoutAmount > currentBalance) {
                throw new IllegalArgumentException("payout amount cannot exceed current running balance");
            }

            double nextBalance = currentBalance - payoutAmount;
            customer.setRunningBalance(nextBalance);
            customer.setTotalPaid(safeDouble(customer.getTotalPaid()) + payoutAmount);
            customer.setLastPayoutDate(req.getDateTo());

            if (note != null) {
                String prefix = "[MONTH_CLOSE_PAYOUT " + req.getDateTo() + " " + payoutAmount + "]";
                String existing = trimToNull(customer.getNotes());
                String combined = existing == null ? prefix + " " + note : existing + " | " + prefix + " " + note;
                customer.setNotes(combined.length() > 500 ? combined.substring(0, 500) : combined);
            }

            customerRecordRepository.save(customer);
            payoutRecorded = payoutAmount;
            customerBalanceAfter = nextBalance;
        }

        return MonthCloseSettlementResponse.builder()
                .dateFrom(req.getDateFrom())
                .dateTo(req.getDateTo())
                .customerType(req.getCustomerType())
                .customerId(customer != null ? customer.getCustomerId() : normalizedCustomerId)
                .customerName(normalizedCustomerName)
                .reconciliationApplied(reconciliationApplied)
                .reconciledSales(reconciledSales)
                .payoutRecorded(payoutRecorded)
                .customerBalanceAfter(customerBalanceAfter)
                .closedBy(actor)
                .closedAt(closedAt)
                .note(note)
                .build();
    }

    private MonthCloseSettlementBulkItemResponse previewMonthCloseBulkItem(
            MonthCloseSettlementBulkRequest request,
            MonthCloseSettlementBulkItemRequest item
    ) {
        if (item.getCustomerType() == null) {
            throw new IllegalArgumentException("customerType is required");
        }
        String normalizedCustomerName = trimToNull(item.getCustomerName());
        if (normalizedCustomerName == null) {
            throw new IllegalArgumentException("customerName is required");
        }
        double payoutAmount = item.getPayoutAmount() == null ? 0.0 : item.getPayoutAmount();
        if (payoutAmount < 0) {
            throw new IllegalArgumentException("payoutAmount cannot be negative");
        }

        String normalizedCustomerId = trimToNull(item.getCustomerId());
        boolean reconciliationApplied =
                item.getCustomerType() == CustomerType.COOPERATIVE
                        && Boolean.TRUE.equals(item.getReconcileOpenCooperative());

        int reconciledSales = 0;
        if (reconciliationApplied) {
            reconciledSales = saleRepository
                    .findByDispatchDateBetweenAndCustomerTypeAndCustomerNameIgnoreCaseAndProductTypeAndReconciledFalse(
                            request.getDateFrom(),
                            request.getDateTo(),
                            item.getCustomerType(),
                            normalizedCustomerName,
                            ProductType.MILK
                    )
                    .size();
        }

        CustomerRecordEntity customer = resolveCustomerForMonthClose(
                normalizedCustomerId,
                normalizedCustomerName,
                item.getCustomerType()
        );
        Double customerBalanceAfter = customer != null ? safeDouble(customer.getRunningBalance()) : null;
        if (payoutAmount > 0) {
            if (customer == null) {
                throw new IllegalArgumentException("Customer record not found for payout");
            }
            double currentBalance = safeDouble(customer.getRunningBalance());
            if (payoutAmount > currentBalance) {
                throw new IllegalArgumentException("payout amount cannot exceed current running balance");
            }
            customerBalanceAfter = currentBalance - payoutAmount;
        }

        return MonthCloseSettlementBulkItemResponse.builder()
                .customerType(item.getCustomerType())
                .customerId(customer != null ? customer.getCustomerId() : normalizedCustomerId)
                .customerName(normalizedCustomerName)
                .success(true)
                .message("PREVIEW")
                .reconciliationApplied(reconciliationApplied)
                .reconciledSales(reconciledSales)
                .payoutRecorded(payoutAmount > 0 ? payoutAmount : 0.0)
                .customerBalanceAfter(customerBalanceAfter)
                .build();
    }

    private void validateMonthCloseBulkRequest(MonthCloseSettlementBulkRequest req) {
        if (req.getDateFrom() == null || req.getDateTo() == null) {
            throw new IllegalArgumentException("dateFrom and dateTo are required");
        }
        if (req.getDateTo().isBefore(req.getDateFrom())) {
            throw new IllegalArgumentException("dateTo cannot be before dateFrom");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("items are required");
        }
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

    private List<CustomerSubscriptionLineEntity> customerSubscriptionLinesForStatement(String customerId) {
        String normalizedCustomerId = trimToNull(customerId);
        if (normalizedCustomerId == null) {
            return List.of();
        }
        return subscriptionLineRepository.findByCustomerIdAndActiveTrueOrderByTaskShiftAscPreferredTimeAscCreatedAtAsc(
                normalizedCustomerId
        );
    }

    private DayPlan computeDayPlan(
            CustomerRecordEntity customer,
            List<CustomerSubscriptionLineEntity> lines,
            LocalDate date,
            boolean paused,
            boolean skipped
    ) {
        if (customer == null || !customer.isActive() || !customer.isSubscriptionActive()) {
            return new DayPlan(0.0, 0.0, false, false);
        }
        if (paused || skipped) {
            return new DayPlan(0.0, 0.0, paused, skipped);
        }

        if (lines != null && !lines.isEmpty()) {
            double qty = 0.0;
            double amount = 0.0;
            for (CustomerSubscriptionLineEntity line : lines) {
                if (line == null || !line.isActive()) {
                    continue;
                }
                if (line.getStartDate() != null && date.isBefore(line.getStartDate())) {
                    continue;
                }
                if (line.getEndDate() != null && date.isAfter(line.getEndDate())) {
                    continue;
                }

                EnumSet<DayOfWeek> lineDays = parseActiveDays(line.getActiveDaysCsv());
                if (!lineDays.isEmpty() && !lineDays.contains(date.getDayOfWeek())) {
                    continue;
                }

                double lineQty = line.getQuantity();
                double lineUnitPrice = line.getUnitPrice() > 0
                        ? line.getUnitPrice()
                        : safeDouble(customer.getDefaultMilkUnitPrice());
                if (lineQty <= 0 || lineUnitPrice <= 0) {
                    continue;
                }
                qty += lineQty;
                amount += (lineQty * lineUnitPrice);
            }
            return new DayPlan(qty, amount, false, false);
        }

        double legacyQty = safeDouble(customer.getDailySubscriptionQty());
        double legacyUnitPrice = safeDouble(customer.getDefaultMilkUnitPrice());
        if (legacyQty <= 0 || legacyUnitPrice <= 0) {
            return new DayPlan(0.0, 0.0, false, false);
        }
        return new DayPlan(legacyQty, legacyQty * legacyUnitPrice, false, false);
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

    private YearMonth parseYearMonth(String monthText) {
        String normalizedMonth = trimToNull(monthText);
        if (normalizedMonth == null) {
            throw new IllegalArgumentException("month is required in YYYY-MM format");
        }
        try {
            return YearMonth.parse(normalizedMonth);
        } catch (Exception e) {
            throw new IllegalArgumentException("month must be in YYYY-MM format");
        }
    }

    private String buildInvoiceNumber(CustomerRecordEntity customer, YearMonth month) {
        String suffixBase = trimToNull(customer.getCustomerId()) != null
                ? customer.getCustomerId()
                : customer.getCustomerName();
        String compact = trimToNull(suffixBase) == null
                ? "CUST"
                : suffixBase.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        String suffix = compact.length() <= 6 ? compact : compact.substring(compact.length() - 6);
        return "INV-" + month.toString().replace("-", "") + "-" + suffix;
    }

    private CustomerSubscriptionInvoiceResponse buildComputedSubscriptionInvoice(
            CustomerRecordEntity customer,
            YearMonth month,
            boolean includeDaily
    ) {
        CustomerSubscriptionStatementResponse statement = subscriptionStatement(
                customer.getCustomerId(),
                month.toString(),
                includeDaily
        );

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        double openingPendingAmount = roundTo2(sumPending(findSalesBeforeDateForCustomer(customer, from)));
        double closingPendingAmount = roundTo2(sumPending(findSalesUpToDateForCustomer(customer, to)));
        double addOnBilledAmount = roundTo2(Math.max(0, statement.getBilledAmount() - statement.getPlannedAmount()));
        double underDeliveryCreditAmount = roundTo2(Math.max(0, statement.getPlannedAmount() - statement.getBilledAmount()));
        LocalDate issueDate = to;
        LocalDate dueDate = to.plusDays(3);

        return CustomerSubscriptionInvoiceResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .customerType(customer.getCustomerType())
                .routeName(customer.getRouteName())
                .collectionPoint(customer.getCollectionPoint())
                .month(month.toString())
                .dateFrom(from)
                .dateTo(to)
                .invoiceNumber(buildInvoiceNumber(customer, month))
                .issueDate(issueDate)
                .dueDate(dueDate)
                .status(SubscriptionInvoiceStatus.DRAFT)
                .statusNote(null)
                .lastStatusUpdatedAt(null)
                .lastStatusUpdatedBy(null)
                .finalizedAt(null)
                .finalizedBy(null)
                .postedAt(null)
                .postedBy(null)
                .subscriptionActive(statement.isSubscriptionActive())
                .pricingMode(statement.getPricingMode())
                .prorationFactor(statement.getProrationFactor())
                .cycleDays(statement.getCycleDays())
                .activePlanDays(statement.getActivePlanDays())
                .pausedDays(statement.getPausedDays())
                .skipDays(statement.getSkipDays())
                .billedDays(statement.getBilledDays())
                .plannedQty(statement.getPlannedQty())
                .plannedAmount(statement.getPlannedAmount())
                .holidayCreditAmount(statement.getHolidayCreditAmount())
                .billedQty(statement.getBilledQty())
                .billedAmount(statement.getBilledAmount())
                .receivedAmount(statement.getReceivedAmount())
                .pendingAmount(statement.getPendingAmount())
                .addOnBilledAmount(addOnBilledAmount)
                .underDeliveryCreditAmount(underDeliveryCreditAmount)
                .openingPendingAmount(openingPendingAmount)
                .closingPendingAmount(closingPendingAmount)
                .currentRunningBalance(roundTo2(safeDouble(customer.getRunningBalance())))
                .invoiceLineItems(buildInvoiceLineItems(
                        statement,
                        openingPendingAmount,
                        closingPendingAmount,
                        addOnBilledAmount,
                        underDeliveryCreditAmount
                ))
                .dailyRows(statement.getDailyRows())
                .build();
    }

    private CustomerSubscriptionInvoiceEntity upsertDraftSubscriptionInvoice(
            CustomerRecordEntity customer,
            YearMonth month,
            CustomerSubscriptionInvoiceResponse computed,
            CustomerSubscriptionInvoiceEntity existing
    ) {
        CustomerSubscriptionInvoiceEntity entity = existing != null
                ? existing
                : CustomerSubscriptionInvoiceEntity.builder()
                .subscriptionInvoiceId("SUBINV_" + UUID.randomUUID().toString().substring(0, 8))
                .customerId(customer.getCustomerId())
                .invoiceMonth(month.toString())
                .build();

        if (existing != null && existing.getStatus() != SubscriptionInvoiceStatus.DRAFT) {
            return existing;
        }

        entity.setCustomerName(customer.getCustomerName());
        entity.setCustomerType(customer.getCustomerType());
        entity.setRouteName(customer.getRouteName());
        entity.setCollectionPoint(customer.getCollectionPoint());
        entity.setInvoiceNumber(computed.getInvoiceNumber());
        entity.setDateFrom(computed.getDateFrom());
        entity.setDateTo(computed.getDateTo());
        entity.setIssueDate(computed.getIssueDate());
        entity.setDueDate(computed.getDueDate());
        entity.setSubscriptionActive(computed.isSubscriptionActive());
        entity.setPricingMode(computed.getPricingMode());
        entity.setStatus(SubscriptionInvoiceStatus.DRAFT);
        entity.setProrationFactor(computed.getProrationFactor());
        entity.setCycleDays(computed.getCycleDays());
        entity.setActivePlanDays(computed.getActivePlanDays());
        entity.setPausedDays(computed.getPausedDays());
        entity.setSkipDays(computed.getSkipDays());
        entity.setBilledDays(computed.getBilledDays());
        entity.setPlannedQty(computed.getPlannedQty());
        entity.setPlannedAmount(computed.getPlannedAmount());
        entity.setHolidayCreditAmount(computed.getHolidayCreditAmount());
        entity.setBilledQty(computed.getBilledQty());
        entity.setBilledAmount(computed.getBilledAmount());
        entity.setReceivedAmount(computed.getReceivedAmount());
        entity.setPendingAmount(computed.getPendingAmount());
        entity.setAddOnBilledAmount(computed.getAddOnBilledAmount());
        entity.setUnderDeliveryCreditAmount(computed.getUnderDeliveryCreditAmount());
        entity.setOpeningPendingAmount(computed.getOpeningPendingAmount());
        entity.setClosingPendingAmount(computed.getClosingPendingAmount());
        entity.setCurrentRunningBalance(computed.getCurrentRunningBalance());
        if (entity.getLastStatusUpdatedAt() == null) {
            entity.setLastStatusUpdatedAt(OffsetDateTime.now());
        }
        return subscriptionInvoiceRepository.save(entity);
    }

    private CustomerSubscriptionInvoiceResponse toSubscriptionInvoiceResponse(
            CustomerSubscriptionInvoiceEntity entity,
            List<CustomerSubscriptionStatementDailyRowResponse> dailyRows
    ) {
        CustomerSubscriptionStatementResponse syntheticStatement = CustomerSubscriptionStatementResponse.builder()
                .plannedQty(entity.getPlannedQty())
                .plannedAmount(entity.getPlannedAmount())
                .holidayCreditAmount(entity.getHolidayCreditAmount())
                .receivedAmount(entity.getReceivedAmount())
                .build();
        return CustomerSubscriptionInvoiceResponse.builder()
                .customerId(entity.getCustomerId())
                .customerName(entity.getCustomerName())
                .customerType(entity.getCustomerType())
                .routeName(entity.getRouteName())
                .collectionPoint(entity.getCollectionPoint())
                .month(entity.getInvoiceMonth())
                .dateFrom(entity.getDateFrom())
                .dateTo(entity.getDateTo())
                .invoiceNumber(entity.getInvoiceNumber())
                .issueDate(entity.getIssueDate())
                .dueDate(entity.getDueDate())
                .status(entity.getStatus())
                .statusNote(entity.getStatusNote())
                .lastStatusUpdatedAt(entity.getLastStatusUpdatedAt())
                .lastStatusUpdatedBy(entity.getLastStatusUpdatedBy())
                .finalizedAt(entity.getFinalizedAt())
                .finalizedBy(entity.getFinalizedBy())
                .postedAt(entity.getPostedAt())
                .postedBy(entity.getPostedBy())
                .subscriptionActive(entity.isSubscriptionActive())
                .pricingMode(entity.getPricingMode())
                .prorationFactor(entity.getProrationFactor())
                .cycleDays(entity.getCycleDays())
                .activePlanDays(entity.getActivePlanDays())
                .pausedDays(entity.getPausedDays())
                .skipDays(entity.getSkipDays())
                .billedDays(entity.getBilledDays())
                .plannedQty(entity.getPlannedQty())
                .plannedAmount(entity.getPlannedAmount())
                .holidayCreditAmount(entity.getHolidayCreditAmount())
                .billedQty(entity.getBilledQty())
                .billedAmount(entity.getBilledAmount())
                .receivedAmount(entity.getReceivedAmount())
                .pendingAmount(entity.getPendingAmount())
                .addOnBilledAmount(entity.getAddOnBilledAmount())
                .underDeliveryCreditAmount(entity.getUnderDeliveryCreditAmount())
                .openingPendingAmount(entity.getOpeningPendingAmount())
                .closingPendingAmount(entity.getClosingPendingAmount())
                .currentRunningBalance(entity.getCurrentRunningBalance())
                .invoiceLineItems(buildInvoiceLineItems(
                        syntheticStatement,
                        entity.getOpeningPendingAmount(),
                        entity.getClosingPendingAmount(),
                        entity.getAddOnBilledAmount(),
                        entity.getUnderDeliveryCreditAmount()
                ))
                .dailyRows(dailyRows == null ? List.of() : dailyRows)
                .build();
    }

    private SubscriptionInvoiceStatusUpdateResponse toStatusUpdateResponse(
            CustomerSubscriptionInvoiceEntity entity,
            SubscriptionInvoiceStatus previousStatus,
            String actor
    ) {
        return SubscriptionInvoiceStatusUpdateResponse.builder()
                .customerId(entity.getCustomerId())
                .month(entity.getInvoiceMonth())
                .invoiceNumber(entity.getInvoiceNumber())
                .previousStatus(previousStatus)
                .currentStatus(entity.getStatus())
                .statusNote(entity.getStatusNote())
                .updatedAt(entity.getLastStatusUpdatedAt())
                .updatedBy(trimToNull(entity.getLastStatusUpdatedBy()) != null ? entity.getLastStatusUpdatedBy() : actor)
                .build();
    }

    private void recordSubscriptionInvoiceStatusAudit(
            CustomerSubscriptionInvoiceEntity entity,
            SubscriptionInvoiceStatus previousStatus,
            String action,
            String actor,
            String statusNote,
            String overrideReason,
            boolean exceptionOverride,
            String approvedBy
    ) {
        if (entity == null || previousStatus == entity.getStatus()) {
            return;
        }
        subscriptionInvoiceStatusAuditRepository.save(
                SubscriptionInvoiceStatusAuditEntity.builder()
                        .subscriptionInvoiceStatusAuditId("SUBINV_AUD_" + UUID.randomUUID().toString().substring(0, 8))
                        .customerId(entity.getCustomerId())
                        .customerName(entity.getCustomerName())
                        .customerType(entity.getCustomerType())
                        .invoiceMonth(entity.getInvoiceMonth())
                        .invoiceNumber(entity.getInvoiceNumber())
                        .previousStatus(previousStatus)
                        .currentStatus(entity.getStatus())
                        .action(action)
                        .statusNote(trimToNull(statusNote))
                        .overrideReason(trimToNull(overrideReason))
                        .exceptionOverride(exceptionOverride)
                        .approvedBy(trimToNull(approvedBy))
                        .actorUsername(normalizeActor(actor))
                        .build()
        );
    }

    private SubscriptionInvoiceStatusAuditResponse toSubscriptionInvoiceStatusAuditResponse(
            SubscriptionInvoiceStatusAuditEntity entity
    ) {
        return SubscriptionInvoiceStatusAuditResponse.builder()
                .subscriptionInvoiceStatusAuditId(entity.getSubscriptionInvoiceStatusAuditId())
                .customerId(entity.getCustomerId())
                .customerName(entity.getCustomerName())
                .customerType(entity.getCustomerType())
                .month(entity.getInvoiceMonth())
                .invoiceNumber(entity.getInvoiceNumber())
                .previousStatus(entity.getPreviousStatus())
                .currentStatus(entity.getCurrentStatus())
                .action(entity.getAction())
                .statusNote(entity.getStatusNote())
                .overrideReason(entity.getOverrideReason())
                .exceptionOverride(entity.isExceptionOverride())
                .approvedBy(entity.getApprovedBy())
                .actorUsername(entity.getActorUsername())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String appendNoteSuffix(String note) {
        String normalized = trimToNull(note);
        return normalized == null ? "" : " | Note: " + normalized;
    }

    private List<SaleEntity> findSalesBeforeDateForCustomer(CustomerRecordEntity customer, LocalDate beforeDate) {
        if (customer == null) {
            return List.of();
        }
        String customerId = trimToNull(customer.getCustomerId());
        if (customerId != null) {
            List<SaleEntity> byId = saleRepository.findByDispatchDateBeforeAndCustomerId(beforeDate, customerId);
            if (!byId.isEmpty()) {
                return byId;
            }
        }
        if (customer.getCustomerType() == null || trimToNull(customer.getCustomerName()) == null) {
            return List.of();
        }
        return saleRepository.findByDispatchDateBeforeAndCustomerTypeAndCustomerNameIgnoreCase(
                beforeDate,
                customer.getCustomerType(),
                customer.getCustomerName()
        );
    }

    private List<SaleEntity> findSalesUpToDateForCustomer(CustomerRecordEntity customer, LocalDate toDateInclusive) {
        if (customer == null) {
            return List.of();
        }
        String customerId = trimToNull(customer.getCustomerId());
        if (customerId != null) {
            List<SaleEntity> byId = saleRepository.findByDispatchDateLessThanEqualAndCustomerId(toDateInclusive, customerId);
            if (!byId.isEmpty()) {
                return byId;
            }
        }
        if (customer.getCustomerType() == null || trimToNull(customer.getCustomerName()) == null) {
            return List.of();
        }
        return saleRepository.findByDispatchDateLessThanEqualAndCustomerTypeAndCustomerNameIgnoreCase(
                toDateInclusive,
                customer.getCustomerType(),
                customer.getCustomerName()
        );
    }

    private double sumPending(List<SaleEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (SaleEntity row : rows) {
            if (row == null) {
                continue;
            }
            total += safeDouble(row.getPendingAmount());
        }
        return total;
    }

    private List<CustomerSubscriptionInvoiceLineItemResponse> buildInvoiceLineItems(
            CustomerSubscriptionStatementResponse statement,
            double openingPendingAmount,
            double closingPendingAmount,
            double addOnBilledAmount,
            double underDeliveryCreditAmount
    ) {
        List<CustomerSubscriptionInvoiceLineItemResponse> items = new ArrayList<>();
        items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                .code("OPENING_PENDING")
                .label("Opening pending balance")
                .quantity(0.0)
                .unitPrice(0.0)
                .amount(roundTo2(openingPendingAmount))
                .note("Pending carried from previous cycles.")
                .build());
        items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                .code("PLANNED_SUBSCRIPTION")
                .label("Planned subscription charge")
                .quantity(roundTo2(statement.getPlannedQty()))
                .unitPrice(0.0)
                .amount(roundTo2(statement.getPlannedAmount()))
                .note("Month plan after pause/skip and proration.")
                .build());
        if (statement.getHolidayCreditAmount() > 0) {
            items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                    .code("HOLIDAY_CREDIT")
                    .label("Holiday/skip credit")
                    .quantity(0.0)
                    .unitPrice(0.0)
                    .amount(roundTo2(-statement.getHolidayCreditAmount()))
                    .note("Credit for configured skip/holiday days.")
                    .build());
        }
        if (addOnBilledAmount > 0) {
            items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                    .code("ADD_ON_BILLED")
                    .label("Extra billed above plan")
                    .quantity(0.0)
                    .unitPrice(0.0)
                    .amount(roundTo2(addOnBilledAmount))
                    .note("Extra quantity or non-plan deliveries billed in month.")
                    .build());
        }
        if (underDeliveryCreditAmount > 0) {
            items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                    .code("UNDER_DELIVERY_CREDIT")
                    .label("Under-delivery adjustment")
                    .quantity(0.0)
                    .unitPrice(0.0)
                    .amount(roundTo2(-underDeliveryCreditAmount))
                    .note("Delivered below planned amount in month.")
                    .build());
        }
        items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                .code("PAYMENT_RECEIVED")
                .label("Payments received")
                .quantity(0.0)
                .unitPrice(0.0)
                .amount(roundTo2(-statement.getReceivedAmount()))
                .note("Collection received during the month.")
                .build());
        items.add(CustomerSubscriptionInvoiceLineItemResponse.builder()
                .code("CLOSING_PENDING")
                .label("Closing pending balance")
                .quantity(0.0)
                .unitPrice(0.0)
                .amount(roundTo2(closingPendingAmount))
                .note("Total pending by end of invoice month.")
                .build());
        return items;
    }

    private CustomerSubscriptionInvoiceSummaryResponse toSubscriptionInvoiceSummary(
            CustomerSubscriptionInvoiceResponse invoice
    ) {
        return CustomerSubscriptionInvoiceSummaryResponse.builder()
                .customerId(invoice.getCustomerId())
                .customerName(invoice.getCustomerName())
                .customerType(invoice.getCustomerType())
                .routeName(invoice.getRouteName())
                .month(invoice.getMonth())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .lastStatusUpdatedAt(invoice.getLastStatusUpdatedAt())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .plannedAmount(invoice.getPlannedAmount())
                .holidayCreditAmount(invoice.getHolidayCreditAmount())
                .billedAmount(invoice.getBilledAmount())
                .receivedAmount(invoice.getReceivedAmount())
                .pendingAmount(invoice.getPendingAmount())
                .openingPendingAmount(invoice.getOpeningPendingAmount())
                .closingPendingAmount(invoice.getClosingPendingAmount())
                .addOnBilledAmount(invoice.getAddOnBilledAmount())
                .underDeliveryCreditAmount(invoice.getUnderDeliveryCreditAmount())
                .prorationFactor(invoice.getProrationFactor())
                .build();
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private double roundTo4(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private String csvValue(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }

    @Transactional
    public DeliveryChecklistItemResponse updateDelivery(
            String saleId,
            UpdateSaleDeliveryRequest req,
            String actorUsername,
            boolean privilegedActor
    ) {
        SaleEntity entity = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));

        boolean delivered = Boolean.TRUE.equals(req.getDelivered());
        if (delivered && entity.getProductType() == ProductType.MILK) {
            LocalDate effectiveBatchDate = entity.getBatchDate() != null ? entity.getBatchDate() : entity.getDispatchDate();
            Shift effectiveBatchShift = entity.getBatchShift() != null ? entity.getBatchShift() : Shift.AM;
            boolean overrideWithdrawal = Boolean.TRUE.equals(req.getOverrideWithdrawalLock());

            if (overrideWithdrawal && !privilegedActor) {
                throw new IllegalArgumentException("Only ADMIN/MANAGER can override withdrawal lock");
            }

            validateMilkRule(
                    entity.getProductType(),
                    effectiveBatchDate,
                    effectiveBatchShift,
                    overrideWithdrawal,
                    req.getOverrideReason(),
                    entity.getSaleId(),
                    entity.getDispatchDate(),
                    entity.getCustomerName(),
                    actorUsername,
                    "DELIVERY"
            );

            entity.setBatchDate(effectiveBatchDate);
            entity.setBatchShift(effectiveBatchShift);
        }

        entity.setDelivered(delivered);
        entity.setDeliveryNote(trimToNull(req.getDeliveryNote()));

        double previousPending = entity.getPendingAmount();
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

        if (Boolean.TRUE.equals(entity.getSubscriptionChargeApplied()) && trimToNull(entity.getCustomerId()) != null) {
            double deltaPending = entity.getPendingAmount() - previousPending;
            if (deltaPending != 0) {
                CustomerRecordEntity customer = customerRecordRepository.findById(entity.getCustomerId())
                        .orElseThrow(() -> new IllegalArgumentException("Linked customer not found for sale"));
                double nextCustomerBalance = adjustCustomerRunningBalance(customer, deltaPending);
                entity.setSubscriptionBalanceImpact(entity.getPendingAmount());
                entity.setCustomerBalanceAfterSale(nextCustomerBalance);
                if (entity.getPendingAmount() <= 0) {
                    entity.setSubscriptionChargeApplied(false);
                }
            }
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

    private CustomerRecordEntity resolveCustomerForMonthClose(
            String customerId,
            String customerName,
            CustomerType customerType
    ) {
        if (customerId != null) {
            CustomerRecordEntity customer = customerRecordRepository.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found for customerId: " + customerId));
            if (customer.getCustomerType() != customerType) {
                throw new IllegalArgumentException("customerId does not match customerType");
            }
            if (!sortable(customer.getCustomerName()).equals(sortable(customerName))) {
                throw new IllegalArgumentException("customerId does not match customerName");
            }
            return customer;
        }

        List<CustomerRecordEntity> matches =
                customerRecordRepository.findByCustomerNameIgnoreCaseAndCustomerType(customerName, customerType);
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple customers found for name and type. Pass customerId.");
        }
        return matches.get(0);
    }

    private CustomerRecordEntity resolveLinkedCustomer(String customerId, String customerName) {
        String normalizedCustomerId = trimToNull(customerId);
        if (normalizedCustomerId != null) {
            return customerRecordRepository.findById(normalizedCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found for customerId: " + normalizedCustomerId));
        }

        String normalizedCustomerName = trimToNull(customerName);
        if (normalizedCustomerName == null) {
            return null;
        }

        List<CustomerRecordEntity> matches = customerRecordRepository.findByCustomerNameIgnoreCase(normalizedCustomerName);
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple customers found for name. Pass customerId.");
        }
        return matches.get(0);
    }

    private void applySubscriptionBalanceImpact(SaleEntity sale, CustomerRecordEntity linkedCustomer) {
        if (linkedCustomer == null
                || !linkedCustomer.isSubscriptionActive()
                || sale.getProductType() != ProductType.MILK
                || sale.getPendingAmount() <= 0) {
            sale.setSubscriptionChargeApplied(false);
            sale.setSubscriptionBalanceImpact(0.0);
            sale.setCustomerBalanceAfterSale(linkedCustomer != null ? safeDouble(linkedCustomer.getRunningBalance()) : null);
            return;
        }

        double nextBalance = adjustCustomerRunningBalance(linkedCustomer, sale.getPendingAmount());
        sale.setSubscriptionChargeApplied(true);
        sale.setSubscriptionBalanceImpact(sale.getPendingAmount());
        sale.setCustomerBalanceAfterSale(nextBalance);
    }

    private double adjustCustomerRunningBalance(CustomerRecordEntity customer, double delta) {
        double current = safeDouble(customer.getRunningBalance());
        double next = current + delta;
        if (next < 0) {
            next = 0;
        }
        customer.setRunningBalance(next);
        customerRecordRepository.save(customer);
        return next;
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

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private void assertExpectedUpdatedAt(SaleEntity entity, OffsetDateTime expectedUpdatedAt) {
        if (expectedUpdatedAt == null) {
            return;
        }
        if (entity.getUpdatedAt() == null || !entity.getUpdatedAt().equals(expectedUpdatedAt)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sale was updated by another user. Refresh and retry."
            );
        }
    }

    private SaleResponse toResponse(SaleEntity e) {
        return SaleResponse.builder()
                .saleId(e.getSaleId())
                .dispatchDate(e.getDispatchDate())
                .customerType(e.getCustomerType())
                .customerId(e.getCustomerId())
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
                .subscriptionChargeApplied(Boolean.TRUE.equals(e.getSubscriptionChargeApplied()))
                .subscriptionBalanceImpact(safeDouble(e.getSubscriptionBalanceImpact()))
                .customerBalanceAfterSale(e.getCustomerBalanceAfterSale())
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

    private record DayPlan(double qty, double amount, boolean paused, boolean skipped) {
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
