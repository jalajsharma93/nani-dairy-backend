package net.nani.dairy.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplateEntity, String> {

    List<TaskTemplateEntity> findByActiveTrueOrderByCreatedAtAsc();

    List<TaskTemplateEntity> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtAsc(
            LocalDate date,
            LocalDate sameDate
    );

    List<TaskTemplateEntity> findByActiveTrueAndStartDateLessThanEqualAndEndDateIsNullOrderByCreatedAtAsc(LocalDate date);
}
