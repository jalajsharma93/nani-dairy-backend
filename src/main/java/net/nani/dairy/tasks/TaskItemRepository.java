package net.nani.dairy.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskItemRepository extends JpaRepository<TaskItemEntity, String> {
    List<TaskItemEntity> findByTaskDateOrderByPriorityDescCreatedAtAsc(LocalDate taskDate);

    List<TaskItemEntity> findAllByOrderByTaskDateAscPriorityDescCreatedAtAsc();

    Optional<TaskItemEntity> findBySourceRefId(String sourceRefId);

    List<TaskItemEntity> findByStatusInAndTaskDateLessThanEqualOrderByTaskDateAscDueTimeAscCreatedAtAsc(
            Collection<TaskStatus> statuses,
            LocalDate date
    );
}
