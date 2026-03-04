package net.nani.dairy.employees;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.CreateEmployeeCompensationAdjustmentRequest;
import net.nani.dairy.employees.dto.EmployeeCompensationAdjustmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeCompensationAdjustmentService {

    private final EmployeeCompensationAdjustmentRepository adjustmentRepository;
    private final EmployeeRepository employeeRepository;

    public List<EmployeeCompensationAdjustmentResponse> list(String month, String employeeId) {
        String effectiveMonth = parseMonth(month).toString();
        String normalizedEmployeeId = trimToNull(employeeId);

        List<EmployeeCompensationAdjustmentEntity> rows = normalizedEmployeeId != null
                ? adjustmentRepository.findByAdjustmentMonthAndEmployeeIdOrderByAdjustmentDateAsc(effectiveMonth, normalizedEmployeeId)
                : adjustmentRepository.findByAdjustmentMonthOrderByAdjustmentDateAscEmployeeIdAsc(effectiveMonth);

        Map<String, String> employeeNameById = resolveEmployeeNameMap(rows);
        return rows.stream()
                .map(row -> toResponse(row, employeeNameById.get(row.getEmployeeId())))
                .toList();
    }

    @Transactional
    public EmployeeCompensationAdjustmentResponse create(CreateEmployeeCompensationAdjustmentRequest req, String actorUsername) {
        String employeeId = normalizeEmployeeId(req.getEmployeeId());
        EmployeeEntity employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        LocalDate adjustmentDate = Objects.requireNonNull(req.getAdjustmentDate(), "adjustmentDate is required");
        CompensationAdjustmentType adjustmentType = Objects.requireNonNull(req.getAdjustmentType(), "adjustmentType is required");
        double amount = normalizeAmount(req.getAmount());
        String notes = trimToNull(req.getNotes());
        String actor = normalizeActor(actorUsername);

        EmployeeCompensationAdjustmentEntity saved = adjustmentRepository.save(
                EmployeeCompensationAdjustmentEntity.builder()
                        .adjustmentId(buildAdjustmentId())
                        .employeeId(employeeId)
                        .adjustmentMonth(YearMonth.from(adjustmentDate).toString())
                        .adjustmentDate(adjustmentDate)
                        .adjustmentType(adjustmentType)
                        .amount(amount)
                        .notes(notes)
                        .createdByUsername(actor)
                        .build()
        );
        return toResponse(saved, employee.getName());
    }

    @Transactional
    public void delete(String adjustmentId) {
        String normalizedId = trimToNull(adjustmentId);
        if (normalizedId == null) {
            throw new IllegalArgumentException("adjustmentId is required");
        }
        if (!adjustmentRepository.existsById(normalizedId)) {
            throw new IllegalArgumentException("Adjustment not found");
        }
        adjustmentRepository.deleteById(normalizedId);
    }

    private Map<String, String> resolveEmployeeNameMap(List<EmployeeCompensationAdjustmentEntity> rows) {
        Set<String> employeeIds = new LinkedHashSet<>();
        for (EmployeeCompensationAdjustmentEntity row : rows) {
            String employeeId = row.getEmployeeId();
            if (employeeId != null && !employeeId.isBlank()) {
                employeeIds.add(employeeId);
            }
        }
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        employeeRepository.findAllById(employeeIds)
                .forEach(employee -> result.put(employee.getEmployeeId(), employee.getName()));
        return result;
    }

    private EmployeeCompensationAdjustmentResponse toResponse(
            EmployeeCompensationAdjustmentEntity row,
            String employeeName
    ) {
        return EmployeeCompensationAdjustmentResponse.builder()
                .adjustmentId(row.getAdjustmentId())
                .employeeId(row.getEmployeeId())
                .employeeName(employeeName)
                .adjustmentMonth(row.getAdjustmentMonth())
                .adjustmentDate(row.getAdjustmentDate())
                .adjustmentType(row.getAdjustmentType())
                .amount(row.getAmount())
                .notes(row.getNotes())
                .createdByUsername(row.getCreatedByUsername())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private String normalizeEmployeeId(String employeeId) {
        String value = trimToNull(employeeId);
        if (value == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        return value;
    }

    private double normalizeAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0d) {
            throw new IllegalArgumentException("amount must be a positive finite number");
        }
        return round2(amount);
    }

    private String normalizeActor(String actorUsername) {
        String actor = trimToNull(actorUsername);
        return actor != null ? actor : "system";
    }

    private String buildAdjustmentId() {
        return "ECA_" + UUID.randomUUID().toString().substring(0, 8);
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

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
