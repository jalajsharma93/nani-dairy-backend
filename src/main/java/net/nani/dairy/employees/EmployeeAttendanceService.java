package net.nani.dairy.employees;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.EmployeeAttendanceMonthlyReportResponse;
import net.nani.dairy.employees.dto.EmployeeAttendanceMonthlyRowResponse;
import net.nani.dairy.employees.dto.EmployeeAttendanceResponse;
import net.nani.dairy.employees.dto.UpsertEmployeeAttendanceRequest;
import net.nani.dairy.milk.Shift;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeAttendanceService {

    private final EmployeeAttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeCompensationAdjustmentRepository compensationAdjustmentRepository;

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
        csv.append("month,dateFrom,dateTo,salaryMode,employeeId,employeeName,employeeType,active,workingDaysInMonth,presentDays,absentDays,presentShifts,absentShifts,shiftsMarked,totalHoursWorked,avgHoursPerPresentDay,overtimeHours,suggestedSalary,bonusAmount,productionIncentiveAmount,advanceAmount,deductionAmount,grossSalary,netPayableSalary\n");

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
