package net.nani.dairy.employees;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.CreateEmployeeCompensationAdjustmentRequest;
import net.nani.dairy.employees.dto.EmployeeCompensationAdjustmentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/attendance/adjustments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeCompensationAdjustmentController {

    private final EmployeeCompensationAdjustmentService compensationAdjustmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<EmployeeCompensationAdjustmentResponse> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String employeeId
    ) {
        return compensationAdjustmentService.list(month, employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeCompensationAdjustmentResponse create(
            @Valid @RequestBody CreateEmployeeCompensationAdjustmentRequest req,
            Authentication authentication
    ) {
        return compensationAdjustmentService.create(req, actor(authentication));
    }

    @DeleteMapping("/{adjustmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String adjustmentId) {
        compensationAdjustmentService.delete(adjustmentId);
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }
}
