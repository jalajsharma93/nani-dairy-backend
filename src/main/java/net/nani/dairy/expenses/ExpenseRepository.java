package net.nani.dairy.expenses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, String> {
    List<ExpenseEntity> findByExpenseDateOrderByCreatedAtDesc(LocalDate expenseDate);

    List<ExpenseEntity> findByCategoryOrderByExpenseDateDescCreatedAtDesc(ExpenseCategory category);

    List<ExpenseEntity> findByPaymentModeOrderByExpenseDateDescCreatedAtDesc(ExpensePaymentMode paymentMode);

    List<ExpenseEntity> findByExpenseDateAndCategoryOrderByCreatedAtDesc(LocalDate expenseDate, ExpenseCategory category);

    List<ExpenseEntity> findByExpenseDateAndPaymentModeOrderByCreatedAtDesc(LocalDate expenseDate, ExpensePaymentMode paymentMode);

    List<ExpenseEntity> findByExpenseDateAndCategoryAndPaymentModeOrderByCreatedAtDesc(
            LocalDate expenseDate,
            ExpenseCategory category,
            ExpensePaymentMode paymentMode
    );
}
