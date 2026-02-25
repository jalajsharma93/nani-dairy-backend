package net.nani.dairy.expenses.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import net.nani.dairy.expenses.ExpenseCategory;
import net.nani.dairy.expenses.ExpensePaymentMode;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExpenseRequest {

    @NotNull
    private LocalDate expenseDate;

    @NotNull
    private ExpenseCategory category;

    @NotNull
    @Positive
    private Double amount;

    @NotNull
    private ExpensePaymentMode paymentMode;

    private String referenceNo;
    private String counterparty;
    private String notes;
}
