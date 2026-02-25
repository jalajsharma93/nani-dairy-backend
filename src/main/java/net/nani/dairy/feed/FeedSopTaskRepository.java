package net.nani.dairy.feed;

import net.nani.dairy.auth.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeedSopTaskRepository extends JpaRepository<FeedSopTaskEntity, String> {
    List<FeedSopTaskEntity> findAllByOrderByTaskDateAscCreatedAtAsc();
    List<FeedSopTaskEntity> findByTaskDateOrderByPriorityDescCreatedAtAsc(LocalDate taskDate);
    List<FeedSopTaskEntity> findByStatusOrderByTaskDateAscPriorityDescCreatedAtAsc(FeedSopTaskStatus status);
    List<FeedSopTaskEntity> findByTaskDateAndStatusOrderByPriorityDescCreatedAtAsc(LocalDate taskDate, FeedSopTaskStatus status);
    List<FeedSopTaskEntity> findByAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(UserRole assignedRole);
    List<FeedSopTaskEntity> findByTaskDateAndAssignedRoleOrderByPriorityDescCreatedAtAsc(LocalDate taskDate, UserRole assignedRole);
    List<FeedSopTaskEntity> findByStatusAndAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(FeedSopTaskStatus status, UserRole assignedRole);
    List<FeedSopTaskEntity> findByTaskDateAndStatusAndAssignedRoleOrderByPriorityDescCreatedAtAsc(
            LocalDate taskDate,
            FeedSopTaskStatus status,
            UserRole assignedRole
    );
}
