package net.nani.dairy.expenses;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.expenses.dto.CreateExpenseRequest;
import net.nani.dairy.expenses.dto.ExpenseResponse;
import net.nani.dairy.expenses.dto.ExpensesSummaryResponse;
import net.nani.dairy.expenses.dto.UpdateExpenseRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExpenseResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) ExpensePaymentMode paymentMode
    ) {
        return expenseService.list(date, category, paymentMode);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ExpenseResponse create(@Valid @RequestBody CreateExpenseRequest req) {
        return expenseService.create(req);
    }

    @PutMapping("/{expenseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExpenseResponse update(@PathVariable String expenseId, @Valid @RequestBody UpdateExpenseRequest req) {
        return expenseService.update(expenseId, req);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ExpensesSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return expenseService.summary(date);
    }
}
