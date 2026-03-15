package net.nani.dairy.employees;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.EmployeeAttendanceMonthlyReportResponse;
import net.nani.dairy.employees.dto.EmployeeAttendanceMonthlyRowResponse;
import net.nani.dairy.employees.dto.EmployeeAttendanceResponse;
import net.nani.dairy.employees.dto.EmployeeMonthlyPayoutResponse;
import net.nani.dairy.employees.dto.EmployeePayslipResponse;
import net.nani.dairy.employees.dto.UpsertEmployeeAttendanceRequest;
import net.nani.dairy.employees.dto.UpsertEmployeeMonthlyPayoutRequest;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.PaymentMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeAttendanceService {

    private final EmployeeAttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeCompensationAdjustmentRepository compensationAdjustmentRepository;
    private final EmployeeMonthlyPayoutRepository monthlyPayoutRepository;

    public List<EmployeeAttendanceResponse> list(
            LocalDate date,
            Shift shift,
            String employeeId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String normalizedEmployeeId = trimToNull(employeeId);
        List<EmployeeAttendanceEntity> rows;

        if (normalizedEmployeeId != null) {
            LocalDate effectiveFrom = dateFrom != null ? dateFrom : (date != null ? date : LocalDate.now().minusDays(30));
            LocalDate effectiveTo = dateTo != null ? dateTo : (date != null ? date : LocalDate.now());
            if (effectiveFrom.isAfter(effectiveTo)) {
                throw new IllegalArgumentException("dateFrom cannot be after dateTo");
            }
            rows = attendanceRepository.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDescShiftAsc(
                    normalizedEmployeeId,
                    effectiveFrom,
                    effectiveTo
            );
        } else {
            LocalDate effectiveDate = date != null ? date : LocalDate.now();
            rows = shift != null
                    ? attendanceRepository.findByAttendanceDateAndShiftOrderByEmployeeIdAsc(effectiveDate, shift)
                    : attendanceRepository.findByAttendanceDateOrderByEmployeeIdAsc(effectiveDate);
        }

        Map<String, String> employeeNameById = resolveEmployeeNameMap(rows);
        return rows.stream()
                .map(row -> toResponse(row, employeeNameById.get(row.getEmployeeId())))
                .toList();
    }

    @Transactional
    public EmployeeAttendanceResponse upsert(UpsertEmployeeAttendanceRequest req, String actorUsername) {
        String employeeId = normalizeEmployeeId(req.getEmployeeId());
        EmployeeEntity employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        LocalDate attendanceDate = Objects.requireNonNull(req.getAttendanceDate(), "attendanceDate is required");
        Shift shift = Objects.requireNonNull(req.getShift(), "shift is required");
        AttendanceStatus status = Objects.requireNonNull(req.getStatus(), "status is required");
        Double normalizedHours = normalizeHours(status, req.getHoursWorked());
        String notes = trimToNull(req.getNotes());
        String actor = normalizeActor(actorUsername);

        EmployeeAttendanceEntity entity = attendanceRepository
                .findByEmployeeIdAndAttendanceDateAndShift(employeeId, attendanceDate, shift)
                .orElseGet(() -> EmployeeAttendanceEntity.builder()
                        .attendanceId(buildAttendanceId())
                        .employeeId(employeeId)
                        .attendanceDate(attendanceDate)
                        .shift(shift)
                        .build());

        entity.setStatus(status);
        entity.setHoursWorked(normalizedHours);
        entity.setNotes(notes);
        entity.setMarkedByUsername(actor);

        EmployeeAttendanceEntity saved = attendanceRepository.save(entity);
        return toResponse(saved, employee.getName());
    }

    @Transactional
    public List<EmployeeAttendanceResponse> bulkUpsert(List<UpsertEmployeeAttendanceRequest> entries, String actorUsername) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries are required");
        }
        List<EmployeeAttendanceResponse> rows = new ArrayList<>(entries.size());
        for (UpsertEmployeeAttendanceRequest row : entries) {
            rows.add(upsert(row, actorUsername));
        }
        return rows;
    }

    public EmployeeAttendanceMonthlyReportResponse monthlyReport(
            String month,
            Boolean includeInactive,
            Boolean includeAdjustments,
            SalaryComputationMode salaryMode,
            Double fullTimeDailyRate,
            Double partTimeDailyRate,
            Double fullTimeShiftRate,
            Double partTimeShiftRate,
            Double hourlyRate,
            Double overtimeHourlyRate,
            Double standardHoursPerDay
    ) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate dateFrom = yearMonth.atDay(1);
        LocalDate dateTo = yearMonth.atEndOfMonth();

        boolean effectiveIncludeInactive = Boolean.TRUE.equals(includeInactive);
        boolean effectiveIncludeAdjustments = includeAdjustments == null || includeAdjustments;
        SalaryComputationMode mode = salaryMode != null ? salaryMode : SalaryComputationMode.NONE;
        SalaryInputs salaryInputs = buildSalaryInputs(
                fullTimeDailyRate,
                partTimeDailyRate,
                fullTimeShiftRate,
                partTimeShiftRate,
                hourlyRate,
                overtimeHourlyRate,
                standardHoursPerDay
        );

        List<EmployeeEntity> employees = effectiveIncludeInactive
                ? employeeRepository.findAll()
                : employeeRepository.findByIsActive(true);
        employees.sort(
                Comparator.comparing(EmployeeEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(EmployeeEntity::getEmployeeId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        );

        Map<String, EmployeeEntity> employeeById = employees.stream()
                .collect(Collectors.toMap(EmployeeEntity::getEmployeeId, e -> e, (first, ignored) -> first, LinkedHashMap::new));

        List<EmployeeAttendanceEntity> attendanceRows = attendanceRepository
                .findByAttendanceDateBetweenOrderByAttendanceDateAscEmployeeIdAsc(dateFrom, dateTo);
        Map<String, AttendanceAccumulator> accumulatorByEmployeeId = new HashMap<>();

        for (EmployeeAttendanceEntity row : attendanceRows) {
            String employeeId = row.getEmployeeId();
            if (employeeId == null || employeeId.isBlank()) {
                continue;
            }
            if (!employeeById.containsKey(employeeId)) {
                continue;
            }

            AttendanceAccumulator accumulator = accumulatorByEmployeeId
                    .computeIfAbsent(employeeId, ignored -> new AttendanceAccumulator());
            accumulator.accept(row);
        }

        Map<String, AdjustmentAccumulator> adjustmentByEmployeeId = new HashMap<>();
        if (effectiveIncludeAdjustments) {
            List<EmployeeCompensationAdjustmentEntity> adjustments = compensationAdjustmentRepository
                    .findByAdjustmentMonthOrderByAdjustmentDateAscEmployeeIdAsc(yearMonth.toString());
            for (EmployeeCompensationAdjustmentEntity row : adjustments) {
                String employeeId = row.getEmployeeId();
                if (employeeId == null || employeeId.isBlank()) {
                    continue;
                }
                if (!employeeById.containsKey(employeeId)) {
                    continue;
                }
                AdjustmentAccumulator accumulator = adjustmentByEmployeeId
                        .computeIfAbsent(employeeId, ignored -> new AdjustmentAccumulator());
                accumulator.accept(row);
            }
        }

        List<String> employeeIds = employees.stream()
                .map(EmployeeEntity::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, EmployeeMonthlyPayoutEntity> payoutByEmployeeId = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            List<EmployeeMonthlyPayoutEntity> payoutRows = monthlyPayoutRepository
                    .findByPayoutMonthAndEmployeeIdIn(yearMonth.toString(), employeeIds);
            for (EmployeeMonthlyPayoutEntity payout : payoutRows) {
                payoutByEmployeeId.put(payout.getEmployeeId(), payout);
            }
        }

        List<EmployeeAttendanceMonthlyRowResponse> reportRows = new ArrayList<>(employees.size());
        int totalPresentDays = 0;
        int totalAbsentDays = 0;
        int totalPresentShifts = 0;
        int totalAbsentShifts = 0;
        double totalHoursWorked = 0d;
        double totalOvertimeHours = 0d;
        double totalSuggestedSalary = 0d;
        double totalBonusAmount = 0d;
        double totalProductionIncentiveAmount = 0d;
        double totalAdvanceAmount = 0d;
        double totalDeductionAmount = 0d;
        double totalGrossSalary = 0d;
        double totalNetPayableSalary = 0d;

        for (EmployeeEntity employee : employees) {
            AttendanceAccumulator accumulator = accumulatorByEmployeeId.get(employee.getEmployeeId());
            if (accumulator == null) {
                accumulator = new AttendanceAccumulator();
            }

            int presentDays = accumulator.presentDays();
            int absentDays = accumulator.absentDays();
            int presentShifts = accumulator.presentShifts;
            int absentShifts = accumulator.absentShifts;
            int shiftsMarked = presentShifts + absentShifts;
            double totalHours = round2(accumulator.totalHoursWorked);
            double avgHours = presentDays > 0 ? round2(totalHours / presentDays) : 0d;
            double overtimeHours = presentDays > 0
                    ? round2(Math.max(0d, totalHours - (presentDays * salaryInputs.standardHoursPerDay)))
                    : 0d;
            double suggestedSalary = round2(computeSuggestedSalary(
                    mode,
                    employee.getType(),
                    presentDays,
                    presentShifts,
                    totalHours,
                    overtimeHours,
                    salaryInputs
            ));
            AdjustmentAccumulator adjustmentAccumulator = adjustmentByEmployeeId.get(employee.getEmployeeId());
            if (adjustmentAccumulator == null) {
                adjustmentAccumulator = new AdjustmentAccumulator();
            }
            double bonusAmount = round2(adjustmentAccumulator.bonusAmount);
            double productionIncentiveAmount = round2(adjustmentAccumulator.productionIncentiveAmount);
            double advanceAmount = round2(adjustmentAccumulator.advanceAmount);
            double deductionAmount = round2(adjustmentAccumulator.deductionAmount);
            double grossSalary = round2(suggestedSalary + bonusAmount + productionIncentiveAmount);
            double netPayableSalary = round2(grossSalary - advanceAmount - deductionAmount);
            EmployeeMonthlyPayoutEntity payout = payoutByEmployeeId.get(employee.getEmployeeId());
            EmployeeMonthlyPayoutStatus payoutStatus = payout != null && payout.getPayoutStatus() != null
                    ? payout.getPayoutStatus()
                    : (netPayableSalary <= 0 ? EmployeeMonthlyPayoutStatus.PAID : EmployeeMonthlyPayoutStatus.PENDING);
            double paidAmount = payout != null ? round2(Math.max(0d, payout.getPaidAmount())) : 0d;
            if (payoutStatus == EmployeeMonthlyPayoutStatus.PAID && paidAmount <= 0d) {
                paidAmount = netPayableSalary;
            }
            double pendingAmount = round2(Math.max(0d, netPayableSalary - paidAmount));

            reportRows.add(EmployeeAttendanceMonthlyRowResponse.builder()
                    .employeeId(employee.getEmployeeId())
                    .employeeName(employee.getName())
                    .employeeType(employee.getType())
                    .active(employee.isActive())
                    .workingDaysInMonth(yearMonth.lengthOfMonth())
                    .presentDays(presentDays)
                    .absentDays(absentDays)
                    .presentShifts(presentShifts)
                    .absentShifts(absentShifts)
                    .shiftsMarked(shiftsMarked)
                    .totalHoursWorked(totalHours)
                    .avgHoursPerPresentDay(avgHours)
                    .overtimeHours(overtimeHours)
                    .suggestedSalary(suggestedSalary)
                    .bonusAmount(bonusAmount)
                    .productionIncentiveAmount(productionIncentiveAmount)
                    .advanceAmount(advanceAmount)
                    .deductionAmount(deductionAmount)
                    .grossSalary(grossSalary)
                    .netPayableSalary(netPayableSalary)
                    .payoutId(payout != null ? payout.getPayoutId() : null)
                    .payoutStatus(payoutStatus)
                    .paidAmount(paidAmount)
                    .pendingAmount(pendingAmount)
                    .payoutPaymentMode(payout != null ? payout.getPaymentMode() : null)
                    .payoutReferenceNo(payout != null ? payout.getPaymentReferenceNo() : null)
                    .payoutUpdatedAt(payout != null ? payout.getUpdatedAt() : null)
                    .build());

            totalPresentDays += presentDays;
            totalAbsentDays += absentDays;
            totalPresentShifts += presentShifts;
            totalAbsentShifts += absentShifts;
            totalHoursWorked += totalHours;
            totalOvertimeHours += overtimeHours;
            totalSuggestedSalary += suggestedSalary;
            totalBonusAmount += bonusAmount;
            totalProductionIncentiveAmount += productionIncentiveAmount;
            totalAdvanceAmount += advanceAmount;
            totalDeductionAmount += deductionAmount;
            totalGrossSalary += grossSalary;
            totalNetPayableSalary += netPayableSalary;
        }

        return EmployeeAttendanceMonthlyReportResponse.builder()
                .month(yearMonth.toString())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeInactive(effectiveIncludeInactive)
                .includeAdjustments(effectiveIncludeAdjustments)
                .salaryMode(mode)
                .fullTimeDailyRate(salaryInputs.fullTimeDailyRate)
                .partTimeDailyRate(salaryInputs.partTimeDailyRate)
                .fullTimeShiftRate(salaryInputs.fullTimeShiftRate)
                .partTimeShiftRate(salaryInputs.partTimeShiftRate)
                .hourlyRate(salaryInputs.hourlyRate)
                .overtimeHourlyRate(salaryInputs.overtimeHourlyRate)
                .standardHoursPerDay(salaryInputs.standardHoursPerDay)
                .totalEmployees(reportRows.size())
                .totalPresentDays(totalPresentDays)
                .totalAbsentDays(totalAbsentDays)
                .totalPresentShifts(totalPresentShifts)
                .totalAbsentShifts(totalAbsentShifts)
                .totalHoursWorked(round2(totalHoursWorked))
                .totalOvertimeHours(round2(totalOvertimeHours))
                .totalSuggestedSalary(round2(totalSuggestedSalary))
                .totalBonusAmount(round2(totalBonusAmount))
                .totalProductionIncentiveAmount(round2(totalProductionIncentiveAmount))
                .totalAdvanceAmount(round2(totalAdvanceAmount))
                .totalDeductionAmount(round2(totalDeductionAmount))
                .totalGrossSalary(round2(totalGrossSalary))
                .totalNetPayableSalary(round2(totalNetPayableSalary))
                .rows(reportRows)
                .build();
    }

    public String monthlyReportCsv(
            String month,
            Boolean includeInactive,
            Boolean includeAdjustments,
            SalaryComputationMode salaryMode,
            Double fullTimeDailyRate,
            Double partTimeDailyRate,
            Double fullTimeShiftRate,
            Double partTimeShiftRate,
            Double hourlyRate,
            Double overtimeHourlyRate,
            Double standardHoursPerDay
    ) {
        EmployeeAttendanceMonthlyReportResponse report = monthlyReport(
                month,
                includeInactive,
                includeAdjustments,
                salaryMode,
                fullTimeDailyRate,
                partTimeDailyRate,
                fullTimeShiftRate,
                partTimeShiftRate,
                hourlyRate,
                overtimeHourlyRate,
                standardHoursPerDay
        );

        StringBuilder csv = new StringBuilder();
        csv.append("month,dateFrom,dateTo,salaryMode,employeeId,employeeName,employeeType,active,workingDaysInMonth,presentDays,absentDays,presentShifts,absentShifts,shiftsMarked,totalHoursWorked,avgHoursPerPresentDay,overtimeHours,suggestedSalary,bonusAmount,productionIncentiveAmount,advanceAmount,deductionAmount,grossSalary,netPayableSalary,payoutStatus,paidAmount,pendingAmount,payoutPaymentMode,payoutReferenceNo\n");

        for (EmployeeAttendanceMonthlyRowResponse row : report.getRows()) {
            appendCsvValue(csv, report.getMonth());
            appendCsvValue(csv, String.valueOf(report.getDateFrom()));
            appendCsvValue(csv, String.valueOf(report.getDateTo()));
            appendCsvValue(csv, report.getSalaryMode().name());
            appendCsvValue(csv, row.getEmployeeId());
            appendCsvValue(csv, row.getEmployeeName());
            appendCsvValue(csv, row.getEmployeeType() != null ? row.getEmployeeType().name() : "");
            appendCsvValue(csv, row.isActive() ? "true" : "false");
            appendCsvValue(csv, String.valueOf(row.getWorkingDaysInMonth()));
            appendCsvValue(csv, String.valueOf(row.getPresentDays()));
            appendCsvValue(csv, String.valueOf(row.getAbsentDays()));
            appendCsvValue(csv, String.valueOf(row.getPresentShifts()));
            appendCsvValue(csv, String.valueOf(row.getAbsentShifts()));
            appendCsvValue(csv, String.valueOf(row.getShiftsMarked()));
            appendCsvValue(csv, String.valueOf(row.getTotalHoursWorked()));
            appendCsvValue(csv, String.valueOf(row.getAvgHoursPerPresentDay()));
            appendCsvValue(csv, String.valueOf(row.getOvertimeHours()));
            appendCsvValue(csv, String.valueOf(row.getSuggestedSalary()));
            appendCsvValue(csv, String.valueOf(row.getBonusAmount()));
            appendCsvValue(csv, String.valueOf(row.getProductionIncentiveAmount()));
            appendCsvValue(csv, String.valueOf(row.getAdvanceAmount()));
            appendCsvValue(csv, String.valueOf(row.getDeductionAmount()));
            appendCsvValue(csv, String.valueOf(row.getGrossSalary()));
            appendCsvValue(csv, String.valueOf(row.getNetPayableSalary()));
            appendCsvValue(csv, row.getPayoutStatus() != null ? row.getPayoutStatus().name() : "");
            appendCsvValue(csv, String.valueOf(row.getPaidAmount()));
            appendCsvValue(csv, String.valueOf(row.getPendingAmount()));
            appendCsvValue(csv, row.getPayoutPaymentMode() != null ? row.getPayoutPaymentMode().name() : "");
            appendCsvValue(csv, row.getPayoutReferenceNo());
            csv.append('\n');
        }

        csv.append('\n');
        csv.append("summary,totalEmployees,totalPresentDays,totalAbsentDays,totalPresentShifts,totalAbsentShifts,totalHoursWorked,totalOvertimeHours,totalSuggestedSalary,totalBonusAmount,totalProductionIncentiveAmount,totalAdvanceAmount,totalDeductionAmount,totalGrossSalary,totalNetPayableSalary\n");
        appendCsvValue(csv, "summary");
        appendCsvValue(csv, String.valueOf(report.getTotalEmployees()));
        appendCsvValue(csv, String.valueOf(report.getTotalPresentDays()));
        appendCsvValue(csv, String.valueOf(report.getTotalAbsentDays()));
        appendCsvValue(csv, String.valueOf(report.getTotalPresentShifts()));
        appendCsvValue(csv, String.valueOf(report.getTotalAbsentShifts()));
        appendCsvValue(csv, String.valueOf(report.getTotalHoursWorked()));
        appendCsvValue(csv, String.valueOf(report.getTotalOvertimeHours()));
        appendCsvValue(csv, String.valueOf(report.getTotalSuggestedSalary()));
        appendCsvValue(csv, String.valueOf(report.getTotalBonusAmount()));
        appendCsvValue(csv, String.valueOf(report.getTotalProductionIncentiveAmount()));
        appendCsvValue(csv, String.valueOf(report.getTotalAdvanceAmount()));
        appendCsvValue(csv, String.valueOf(report.getTotalDeductionAmount()));
        appendCsvValue(csv, String.valueOf(report.getTotalGrossSalary()));
        appendCsvValue(csv, String.valueOf(report.getTotalNetPayableSalary()));
        csv.append('\n');

        return csv.toString();
    }

    public List<EmployeeMonthlyPayoutResponse> monthlyPayouts(String month) {
        YearMonth yearMonth = parseMonth(month);
        List<EmployeeMonthlyPayoutEntity> rows = monthlyPayoutRepository
                .findByPayoutMonthOrderByEmployeeIdAsc(yearMonth.toString());

        if (rows.isEmpty()) {
            return List.of();
        }

        Set<String> employeeIds = rows.stream()
                .map(EmployeeMonthlyPayoutEntity::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> nameByEmployeeId = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            employeeRepository.findAllById(employeeIds)
                    .forEach(row -> nameByEmployeeId.put(row.getEmployeeId(), row.getName()));
        }

        return rows.stream()
                .map(row -> toMonthlyPayoutResponse(row, nameByEmployeeId.get(row.getEmployeeId())))
                .toList();
    }

    @Transactional
    public EmployeeMonthlyPayoutResponse upsertMonthlyPayout(UpsertEmployeeMonthlyPayoutRequest req, String actorUsername) {
        YearMonth yearMonth = parseMonth(req.getMonth());
        String employeeId = normalizeEmployeeId(req.getEmployeeId());
        String actor = normalizeActor(actorUsername);

        EmployeeEntity employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        EmployeeMonthlyPayoutEntity entity = monthlyPayoutRepository
                .findByEmployeeIdAndPayoutMonth(employeeId, yearMonth.toString())
                .orElseGet(() -> EmployeeMonthlyPayoutEntity.builder()
                        .payoutId("PAY_" + UUID.randomUUID().toString().substring(0, 8))
                        .employeeId(employeeId)
                        .payoutMonth(yearMonth.toString())
                        .build());

        double netPayableSalary = req.getNetPayableSalary() != null
                ? normalizeNonNegative(req.getNetPayableSalary(), 0d, "netPayableSalary")
                : (entity.getNetPayableSalary() > 0
                ? entity.getNetPayableSalary()
                : resolveNetPayableForEmployee(yearMonth.toString(), employeeId));
        netPayableSalary = round2(netPayableSalary);

        EmployeeMonthlyPayoutStatus payoutStatus = req.getPayoutStatus() != null
                ? req.getPayoutStatus()
                : (entity.getPayoutStatus() != null
                ? entity.getPayoutStatus()
                : (netPayableSalary <= 0 ? EmployeeMonthlyPayoutStatus.PAID : EmployeeMonthlyPayoutStatus.PENDING));

        double paidAmount = req.getPaidAmount() != null
                ? normalizeNonNegative(req.getPaidAmount(), 0d, "paidAmount")
                : Math.max(0d, entity.getPaidAmount());
        if (payoutStatus == EmployeeMonthlyPayoutStatus.PAID && paidAmount <= 0d) {
            paidAmount = netPayableSalary;
        }
        paidAmount = round2(paidAmount);
        if (netPayableSalary > 0 && paidAmount > netPayableSalary) {
            throw new IllegalArgumentException("paidAmount cannot exceed netPayableSalary");
        }

        entity.setNetPayableSalary(netPayableSalary);
        entity.setPayoutStatus(payoutStatus);
        entity.setPaidAmount(paidAmount);
        entity.setPaymentMode(req.getPaymentMode() != null ? req.getPaymentMode() : entity.getPaymentMode());
        entity.setPaymentReferenceNo(trimToNull(req.getPaymentReferenceNo()));
        entity.setNotes(trimToNull(req.getNotes()));

        OffsetDateTime now = OffsetDateTime.now();
        if (payoutStatus == EmployeeMonthlyPayoutStatus.PENDING) {
            entity.setApprovedAt(null);
            entity.setApprovedByUsername(null);
            entity.setPaidAt(null);
            entity.setPaidByUsername(null);
        } else if (payoutStatus == EmployeeMonthlyPayoutStatus.APPROVED) {
            entity.setApprovedAt(now);
            entity.setApprovedByUsername(actor);
            entity.setPaidAt(null);
            entity.setPaidByUsername(null);
        } else {
            if (entity.getApprovedAt() == null) {
                entity.setApprovedAt(now);
            }
            if (trimToNull(entity.getApprovedByUsername()) == null) {
                entity.setApprovedByUsername(actor);
            }
            entity.setPaidAt(now);
            entity.setPaidByUsername(actor);
        }

        EmployeeMonthlyPayoutEntity saved = monthlyPayoutRepository.save(entity);
        return toMonthlyPayoutResponse(saved, employee.getName());
    }

    public EmployeePayslipResponse payslip(
            String month,
            String employeeId,
            Boolean includeAdjustments,
            SalaryComputationMode salaryMode,
            Double fullTimeDailyRate,
            Double partTimeDailyRate,
            Double fullTimeShiftRate,
            Double partTimeShiftRate,
            Double hourlyRate,
            Double overtimeHourlyRate,
            Double standardHoursPerDay
    ) {
        String normalizedEmployeeId = normalizeEmployeeId(employeeId);
        SalaryComputationMode effectiveMode = salaryMode != null ? salaryMode : SalaryComputationMode.DAILY;
        EmployeeAttendanceMonthlyReportResponse report = monthlyReport(
                month,
                true,
                includeAdjustments,
                effectiveMode,
                fullTimeDailyRate,
                partTimeDailyRate,
                fullTimeShiftRate,
                partTimeShiftRate,
                hourlyRate,
                overtimeHourlyRate,
                standardHoursPerDay
        );

        EmployeeAttendanceMonthlyRowResponse row = report.getRows().stream()
                .filter(item -> normalizedEmployeeId.equals(item.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Employee not found in month report"));

        EmployeeMonthlyPayoutEntity payout = monthlyPayoutRepository
                .findByEmployeeIdAndPayoutMonth(normalizedEmployeeId, report.getMonth())
                .orElse(null);

        EmployeeMonthlyPayoutStatus payoutStatus = payout != null && payout.getPayoutStatus() != null
                ? payout.getPayoutStatus()
                : row.getPayoutStatus();
        if (payoutStatus == null) {
            payoutStatus = row.getNetPayableSalary() <= 0 ? EmployeeMonthlyPayoutStatus.PAID : EmployeeMonthlyPayoutStatus.PENDING;
        }
        double paidAmount = payout != null ? round2(Math.max(0d, payout.getPaidAmount())) : round2(row.getPaidAmount());
        if (payoutStatus == EmployeeMonthlyPayoutStatus.PAID && paidAmount <= 0d) {
            paidAmount = row.getNetPayableSalary();
        }
        double pendingAmount = round2(Math.max(0d, row.getNetPayableSalary() - paidAmount));

        return EmployeePayslipResponse.builder()
                .month(report.getMonth())
                .dateFrom(report.getDateFrom())
                .dateTo(report.getDateTo())
                .employeeId(row.getEmployeeId())
                .employeeName(row.getEmployeeName())
                .employeeType(row.getEmployeeType())
                .presentDays(row.getPresentDays())
                .absentDays(row.getAbsentDays())
                .presentShifts(row.getPresentShifts())
                .totalHoursWorked(row.getTotalHoursWorked())
                .suggestedSalary(row.getSuggestedSalary())
                .bonusAmount(row.getBonusAmount())
                .productionIncentiveAmount(row.getProductionIncentiveAmount())
                .advanceAmount(row.getAdvanceAmount())
                .deductionAmount(row.getDeductionAmount())
                .grossSalary(row.getGrossSalary())
                .netPayableSalary(row.getNetPayableSalary())
                .payoutStatus(payoutStatus)
                .paidAmount(paidAmount)
                .pendingAmount(pendingAmount)
                .paymentMode(payout != null ? payout.getPaymentMode() : row.getPayoutPaymentMode())
                .paymentReferenceNo(payout != null ? payout.getPaymentReferenceNo() : row.getPayoutReferenceNo())
                .payoutNotes(payout != null ? payout.getNotes() : null)
                .approvedAt(payout != null ? payout.getApprovedAt() : null)
                .paidAt(payout != null ? payout.getPaidAt() : null)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    public String payslipHtml(
            String month,
            String employeeId,
            Boolean includeAdjustments,
            SalaryComputationMode salaryMode,
            Double fullTimeDailyRate,
            Double partTimeDailyRate,
            Double fullTimeShiftRate,
            Double partTimeShiftRate,
            Double hourlyRate,
            Double overtimeHourlyRate,
            Double standardHoursPerDay
    ) {
        EmployeePayslipResponse payslip = payslip(
                month,
                employeeId,
                includeAdjustments,
                salaryMode,
                fullTimeDailyRate,
                partTimeDailyRate,
                fullTimeShiftRate,
                partTimeShiftRate,
                hourlyRate,
                overtimeHourlyRate,
                standardHoursPerDay
        );

        String status = payslip.getPayoutStatus() != null ? payslip.getPayoutStatus().name() : "PENDING";
        String mode = payslip.getPaymentMode() != null ? payslip.getPaymentMode().name() : "-";
        String referenceNo = trimToNull(payslip.getPaymentReferenceNo());
        String notes = trimToNull(payslip.getPayoutNotes());

        return "<!doctype html>"
                + "<html><head><meta charset=\"utf-8\"/>"
                + "<title>Payslip " + escapeHtml(payslip.getEmployeeId()) + " " + escapeHtml(payslip.getMonth()) + "</title>"
                + "<style>"
                + "body{font-family:Arial,sans-serif;padding:24px;color:#1c2a23;}"
                + "h1{margin:0 0 4px 0;font-size:22px;} .sub{color:#4b6155;margin-bottom:14px;}"
                + "table{border-collapse:collapse;width:100%;margin-top:10px;}"
                + "th,td{border:1px solid #d6dfd8;padding:8px;text-align:left;font-size:13px;}"
                + "th{background:#edf3ee;} .pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#e9f3ea;}"
                + "</style></head><body>"
                + "<h1>NANI Dairy Payslip</h1>"
                + "<div class=\"sub\">Month " + escapeHtml(payslip.getMonth()) + " | " + escapeHtml(payslip.getDateFrom()) + " to " + escapeHtml(payslip.getDateTo()) + "</div>"
                + "<table>"
                + "<tr><th>Employee</th><td>" + escapeHtml(payslip.getEmployeeName()) + " (" + escapeHtml(payslip.getEmployeeId()) + ")</td></tr>"
                + "<tr><th>Type</th><td>" + escapeHtml(payslip.getEmployeeType()) + "</td></tr>"
                + "<tr><th>Attendance</th><td>Present days " + payslip.getPresentDays() + " | Absent days " + payslip.getAbsentDays() + " | Present shifts " + payslip.getPresentShifts() + " | Hours " + round2(payslip.getTotalHoursWorked()) + "</td></tr>"
                + "<tr><th>Salary</th><td>Suggested " + round2(payslip.getSuggestedSalary()) + " | Gross " + round2(payslip.getGrossSalary()) + " | Net " + round2(payslip.getNetPayableSalary()) + "</td></tr>"
                + "<tr><th>Adjustments</th><td>Bonus " + round2(payslip.getBonusAmount()) + " | Incentive " + round2(payslip.getProductionIncentiveAmount()) + " | Advance " + round2(payslip.getAdvanceAmount()) + " | Deduction " + round2(payslip.getDeductionAmount()) + "</td></tr>"
                + "<tr><th>Payout</th><td><span class=\"pill\">" + escapeHtml(status) + "</span> | Paid " + round2(payslip.getPaidAmount()) + " | Pending " + round2(payslip.getPendingAmount()) + "</td></tr>"
                + "<tr><th>Payment mode</th><td>" + escapeHtml(mode) + "</td></tr>"
                + "<tr><th>Reference</th><td>" + escapeHtml(referenceNo != null ? referenceNo : "-") + "</td></tr>"
                + "<tr><th>Notes</th><td>" + escapeHtml(notes != null ? notes : "-") + "</td></tr>"
                + "<tr><th>Generated at</th><td>" + escapeHtml(payslip.getGeneratedAt()) + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }

    private Map<String, String> resolveEmployeeNameMap(List<EmployeeAttendanceEntity> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (EmployeeAttendanceEntity row : rows) {
            if (row.getEmployeeId() != null) {
                ids.add(row.getEmployeeId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        employeeRepository.findAllById(ids).forEach(emp -> names.put(emp.getEmployeeId(), emp.getName()));
        return names;
    }

    private EmployeeAttendanceResponse toResponse(EmployeeAttendanceEntity row, String employeeName) {
        return EmployeeAttendanceResponse.builder()
                .attendanceId(row.getAttendanceId())
                .employeeId(row.getEmployeeId())
                .employeeName(employeeName)
                .attendanceDate(row.getAttendanceDate())
                .shift(row.getShift())
                .status(row.getStatus())
                .hoursWorked(row.getHoursWorked())
                .notes(row.getNotes())
                .markedByUsername(row.getMarkedByUsername())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private EmployeeMonthlyPayoutResponse toMonthlyPayoutResponse(
            EmployeeMonthlyPayoutEntity row,
            String employeeName
    ) {
        double netPayableSalary = round2(Math.max(0d, row.getNetPayableSalary()));
        double paidAmount = round2(Math.max(0d, row.getPaidAmount()));
        if (row.getPayoutStatus() == EmployeeMonthlyPayoutStatus.PAID && paidAmount <= 0d) {
            paidAmount = netPayableSalary;
        }
        double pendingAmount = round2(Math.max(0d, netPayableSalary - paidAmount));

        return EmployeeMonthlyPayoutResponse.builder()
                .payoutId(row.getPayoutId())
                .month(row.getPayoutMonth())
                .employeeId(row.getEmployeeId())
                .employeeName(employeeName)
                .payoutStatus(row.getPayoutStatus())
                .netPayableSalary(netPayableSalary)
                .paidAmount(paidAmount)
                .pendingAmount(pendingAmount)
                .paymentMode(row.getPaymentMode())
                .paymentReferenceNo(row.getPaymentReferenceNo())
                .notes(row.getNotes())
                .approvedByUsername(row.getApprovedByUsername())
                .approvedAt(row.getApprovedAt())
                .paidByUsername(row.getPaidByUsername())
                .paidAt(row.getPaidAt())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private double resolveNetPayableForEmployee(String month, String employeeId) {
        EmployeeAttendanceMonthlyReportResponse report = monthlyReport(
                month,
                true,
                true,
                SalaryComputationMode.DAILY,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return report.getRows().stream()
                .filter(row -> employeeId.equals(row.getEmployeeId()))
                .map(EmployeeAttendanceMonthlyRowResponse::getNetPayableSalary)
                .findFirst()
                .orElse(0d);
    }

    private String normalizeEmployeeId(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        return trimmed;
    }

    private Double normalizeHours(AttendanceStatus status, Double hoursWorked) {
        if (status == AttendanceStatus.ABSENT) {
            if (hoursWorked == null) {
                return 0d;
            }
            if (!Double.isFinite(hoursWorked) || hoursWorked < 0 || hoursWorked > 24) {
                throw new IllegalArgumentException("hoursWorked must be between 0 and 24");
            }
            return hoursWorked;
        }
        if (hoursWorked == null) {
            return 8d;
        }
        if (!Double.isFinite(hoursWorked) || hoursWorked <= 0 || hoursWorked > 24) {
            throw new IllegalArgumentException("hoursWorked must be between 0 and 24 for PRESENT status");
        }
        return hoursWorked;
    }

    private String buildAttendanceId() {
        return "ATT_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private SalaryInputs buildSalaryInputs(
            Double fullTimeDailyRate,
            Double partTimeDailyRate,
            Double fullTimeShiftRate,
            Double partTimeShiftRate,
            Double hourlyRate,
            Double overtimeHourlyRate,
            Double standardHoursPerDay
    ) {
        double effectiveFullTimeDailyRate = normalizeNonNegative(fullTimeDailyRate, 0d, "fullTimeDailyRate");
        double effectivePartTimeDailyRate = normalizeNonNegative(partTimeDailyRate, 0d, "partTimeDailyRate");
        double effectiveFullTimeShiftRate = normalizeNonNegative(fullTimeShiftRate, 0d, "fullTimeShiftRate");
        double effectivePartTimeShiftRate = normalizeNonNegative(partTimeShiftRate, 0d, "partTimeShiftRate");
        double effectiveHourlyRate = normalizeNonNegative(hourlyRate, 0d, "hourlyRate");
        double effectiveOvertimeHourlyRate = normalizeNonNegative(overtimeHourlyRate, 0d, "overtimeHourlyRate");
        double effectiveStandardHours = standardHoursPerDay == null ? 8d : standardHoursPerDay;
        if (!Double.isFinite(effectiveStandardHours) || effectiveStandardHours <= 0 || effectiveStandardHours > 24) {
            throw new IllegalArgumentException("standardHoursPerDay must be between 0 and 24");
        }
        return new SalaryInputs(
                round2(effectiveFullTimeDailyRate),
                round2(effectivePartTimeDailyRate),
                round2(effectiveFullTimeShiftRate),
                round2(effectivePartTimeShiftRate),
                round2(effectiveHourlyRate),
                round2(effectiveOvertimeHourlyRate),
                round2(effectiveStandardHours)
        );
    }

    private double computeSuggestedSalary(
            SalaryComputationMode mode,
            EmployeeType employeeType,
            int presentDays,
            int presentShifts,
            double totalHours,
            double overtimeHours,
            SalaryInputs inputs
    ) {
        EmployeeType type = employeeType != null ? employeeType : EmployeeType.FULL_TIME;
        double dailyRate = type == EmployeeType.PART_TIME ? inputs.partTimeDailyRate : inputs.fullTimeDailyRate;
        double shiftRate = type == EmployeeType.PART_TIME ? inputs.partTimeShiftRate : inputs.fullTimeShiftRate;
        return switch (mode) {
            case NONE -> 0d;
            case DAILY -> presentDays * dailyRate;
            case SHIFT -> presentShifts * shiftRate;
            case HOURLY -> totalHours * inputs.hourlyRate;
            case DAILY_PLUS_OVERTIME -> (presentDays * dailyRate) + (overtimeHours * inputs.overtimeHourlyRate);
        };
    }

    private YearMonth parseMonth(String month) {
        String value = trimToNull(month);
        if (value == null) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("month must be in YYYY-MM format");
        }
    }

    private double normalizeNonNegative(Double value, double fallback, String fieldName) {
        if (value == null) {
            return fallback;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite number");
        }
        return value;
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private void appendCsvValue(StringBuilder csv, String value) {
        if (csv.length() > 0 && csv.charAt(csv.length() - 1) != '\n') {
            csv.append(',');
        }
        csv.append(csvEscape(value));
    }

    private String csvEscape(String raw) {
        String value = raw == null ? "" : raw;
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String escapeHtml(Object value) {
        return escapeHtml(value != null ? String.valueOf(value) : null);
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized != null ? normalized : "system";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class AttendanceAccumulator {
        private int presentShifts = 0;
        private int absentShifts = 0;
        private double totalHoursWorked = 0d;
        private final Map<LocalDate, DayBucket> dayBuckets = new HashMap<>();

        private void accept(EmployeeAttendanceEntity row) {
            DayBucket bucket = dayBuckets.computeIfAbsent(row.getAttendanceDate(), ignored -> new DayBucket());
            if (row.getStatus() == AttendanceStatus.PRESENT) {
                presentShifts += 1;
                bucket.hasPresent = true;
            } else {
                absentShifts += 1;
                bucket.hasAbsent = true;
            }
            double hours = row.getHoursWorked() == null ? 0d : row.getHoursWorked();
            if (Double.isFinite(hours) && hours > 0) {
                totalHoursWorked += hours;
            }
        }

        private int presentDays() {
            int count = 0;
            for (DayBucket bucket : dayBuckets.values()) {
                if (bucket.hasPresent) {
                    count += 1;
                }
            }
            return count;
        }

        private int absentDays() {
            int count = 0;
            for (DayBucket bucket : dayBuckets.values()) {
                if (!bucket.hasPresent && bucket.hasAbsent) {
                    count += 1;
                }
            }
            return count;
        }
    }

    private static class AdjustmentAccumulator {
        private double bonusAmount = 0d;
        private double productionIncentiveAmount = 0d;
        private double advanceAmount = 0d;
        private double deductionAmount = 0d;

        private void accept(EmployeeCompensationAdjustmentEntity row) {
            double amount = row.getAmount();
            if (!Double.isFinite(amount) || amount <= 0) {
                return;
            }
            switch (row.getAdjustmentType()) {
                case BONUS -> bonusAmount += amount;
                case PRODUCTION_INCENTIVE -> productionIncentiveAmount += amount;
                case ADVANCE -> advanceAmount += amount;
                case DEDUCTION -> deductionAmount += amount;
            }
        }
    }

    private static class DayBucket {
        private boolean hasPresent = false;
        private boolean hasAbsent = false;
    }

    private record SalaryInputs(
            double fullTimeDailyRate,
            double partTimeDailyRate,
            double fullTimeShiftRate,
            double partTimeShiftRate,
            double hourlyRate,
            double overtimeHourlyRate,
            double standardHoursPerDay
    ) {
    }
}
