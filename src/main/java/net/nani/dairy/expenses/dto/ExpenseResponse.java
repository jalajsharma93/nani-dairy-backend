package net.nani.dairy.expenses.dto;

import lombok.*;
import net.nani.dairy.expenses.ExpenseCategory;
import net.nani.dairy.expenses.ExpensePaymentMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponse {
    private String expenseId;
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private double amount;
    private ExpensePaymentMode paymentMode;
    private String referenceNo;
    private String counterparty;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
