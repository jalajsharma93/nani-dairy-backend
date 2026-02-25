package net.nani.dairy.expenses;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.expenses.dto.CreateExpenseRequest;
import net.nani.dairy.expenses.dto.ExpenseResponse;
import net.nani.dairy.expenses.dto.ExpensesSummaryResponse;
import net.nani.dairy.expenses.dto.UpdateExpenseRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public List<ExpenseResponse> list(LocalDate date, ExpenseCategory category, ExpensePaymentMode paymentMode) {
        List<ExpenseEntity> rows;
        if (date != null && category != null && paymentMode != null) {
            rows = expenseRepository.findByExpenseDateAndCategoryAndPaymentModeOrderByCreatedAtDesc(date, category, paymentMode);
        } else if (date != null && category != null) {
            rows = expenseRepository.findByExpenseDateAndCategoryOrderByCreatedAtDesc(date, category);
        } else if (date != null && paymentMode != null) {
            rows = expenseRepository.findByExpenseDateAndPaymentModeOrderByCreatedAtDesc(date, paymentMode);
        } else if (date != null) {
            rows = expenseRepository.findByExpenseDateOrderByCreatedAtDesc(date);
        } else if (category != null) {
            rows = expenseRepository.findByCategoryOrderByExpenseDateDescCreatedAtDesc(category);
        } else if (paymentMode != null) {
            rows = expenseRepository.findByPaymentModeOrderByExpenseDateDescCreatedAtDesc(paymentMode);
        } else {
            rows = expenseRepository.findAll();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    public ExpenseResponse create(CreateExpenseRequest req) {
        ExpenseEntity entity = ExpenseEntity.builder()
                .expenseId(buildId())
                .expenseDate(req.getExpenseDate())
                .category(req.getCategory())
                .amount(req.getAmount())
                .paymentMode(req.getPaymentMode())
                .referenceNo(trimToNull(req.getReferenceNo()))
                .counterparty(trimToNull(req.getCounterparty()))
                .notes(trimToNull(req.getNotes()))
                .build();
        return toResponse(expenseRepository.save(entity));
    }

    public ExpenseResponse update(String expenseId, UpdateExpenseRequest req) {
        ExpenseEntity entity = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        entity.setExpenseDate(req.getExpenseDate());
        entity.setCategory(req.getCategory());
        entity.setAmount(req.getAmount());
        entity.setPaymentMode(req.getPaymentMode());
        entity.setReferenceNo(trimToNull(req.getReferenceNo()));
        entity.setCounterparty(trimToNull(req.getCounterparty()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toResponse(expenseRepository.save(entity));
    }

    public ExpensesSummaryResponse summary(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        var rows = expenseRepository.findByExpenseDateOrderByCreatedAtDesc(effectiveDate);

        double total = 0;
        double salary = 0;
        for (var row : rows) {
            total += row.getAmount();
            if (row.getCategory() == ExpenseCategory.SALARY) {
                salary += row.getAmount();
            }
        }

        return new ExpensesSummaryResponse(
                effectiveDate,
                total,
                salary,
                total - salary,
                rows.size()
        );
    }

    private ExpenseResponse toResponse(ExpenseEntity e) {
        return ExpenseResponse.builder()
                .expenseId(e.getExpenseId())
                .expenseDate(e.getExpenseDate())
                .category(e.getCategory())
                .amount(e.getAmount())
                .paymentMode(e.getPaymentMode())
                .referenceNo(e.getReferenceNo())
                .counterparty(e.getCounterparty())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildId() {
        return "EXP_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
