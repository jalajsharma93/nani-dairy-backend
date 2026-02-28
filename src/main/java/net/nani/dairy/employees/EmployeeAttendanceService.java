package net.nani.dairy.employees;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.EmployeeAttendanceResponse;
import net.nani.dairy.employees.dto.UpsertEmployeeAttendanceRequest;
import net.nani.dairy.milk.Shift;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeAttendanceService {

    private final EmployeeAttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

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
}
