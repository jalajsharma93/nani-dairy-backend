package net.nani.dairy.employees;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.employees.dto.CreateEmployeeRequest;
import net.nani.dairy.employees.dto.EmployeeResponse;
import net.nani.dairy.employees.dto.UpdateEmployeeRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    public List<EmployeeResponse> listEmployees(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) EmployeeType type
    ) {
        return service.list(active, type);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse createEmployee(@Valid @RequestBody CreateEmployeeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{employeeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse updateEmployee(
            @PathVariable String employeeId,
            @Valid @RequestBody UpdateEmployeeRequest req
    ) {
        return service.update(employeeId, req);
    }
}
