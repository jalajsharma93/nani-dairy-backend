package net.nani.dairy.employees;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.BulkUpsertEmployeeAttendanceRequest;
import net.nani.dairy.employees.dto.EmployeeAttendanceResponse;
import net.nani.dairy.employees.dto.UpsertEmployeeAttendanceRequest;
import net.nani.dairy.milk.Shift;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }
}
