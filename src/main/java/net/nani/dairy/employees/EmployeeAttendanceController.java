package net.nani.dairy.employees;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.BulkUpsertEmployeeAttendanceRequest;
import net.nani.dairy.employees.dto.EmployeeAttendanceMonthlyReportResponse;
import net.nani.dairy.employees.dto.EmployeeAttendanceResponse;
import net.nani.dairy.employees.dto.UpsertEmployeeAttendanceRequest;
import net.nani.dairy.milk.Shift;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees/attendance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeAttendanceController {

    private final EmployeeAttendanceService attendanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<EmployeeAttendanceResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Shift shift,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return attendanceService.list(date, shift, employeeId, dateFrom, dateTo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EmployeeAttendanceResponse upsert(
            @Valid @RequestBody UpsertEmployeeAttendanceRequest req,
            Authentication authentication
    ) {
        return attendanceService.upsert(req, actor(authentication));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<EmployeeAttendanceResponse> bulkUpsert(
            @Valid @RequestBody BulkUpsertEmployeeAttendanceRequest req,
            Authentication authentication
    ) {
        return attendanceService.bulkUpsert(req.getEntries(), actor(authentication));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EmployeeAttendanceMonthlyReportResponse monthlyReport(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Boolean includeInactive,
            @RequestParam(required = false) Boolean includeAdjustments,
            @RequestParam(required = false) SalaryComputationMode salaryMode,
            @RequestParam(required = false) Double fullTimeDailyRate,
            @RequestParam(required = false) Double partTimeDailyRate,
            @RequestParam(required = false) Double fullTimeShiftRate,
            @RequestParam(required = false) Double partTimeShiftRate,
            @RequestParam(required = false) Double hourlyRate,
            @RequestParam(required = false) Double overtimeHourlyRate,
            @RequestParam(required = false) Double standardHoursPerDay
    ) {
        return attendanceService.monthlyReport(
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
    }

    @GetMapping(value = "/monthly/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<String> monthlyReportExport(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Boolean includeInactive,
            @RequestParam(required = false) Boolean includeAdjustments,
            @RequestParam(required = false) SalaryComputationMode salaryMode,
            @RequestParam(required = false) Double fullTimeDailyRate,
            @RequestParam(required = false) Double partTimeDailyRate,
            @RequestParam(required = false) Double fullTimeShiftRate,
            @RequestParam(required = false) Double partTimeShiftRate,
            @RequestParam(required = false) Double hourlyRate,
            @RequestParam(required = false) Double overtimeHourlyRate,
            @RequestParam(required = false) Double standardHoursPerDay
    ) {
        String effectiveMonth = (month == null || month.isBlank()) ? LocalDate.now().toString().substring(0, 7) : month;
        String csv = attendanceService.monthlyReportCsv(
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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance-" + effectiveMonth + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }
}
