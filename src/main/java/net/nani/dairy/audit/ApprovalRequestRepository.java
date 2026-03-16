package net.nani.dairy.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, String> {
    List<ApprovalRequestEntity> findAllByOrderByCreatedAtDesc();

    List<ApprovalRequestEntity> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequestEntity> findByRequestedByUsernameIgnoreCaseOrderByCreatedAtDesc(String requestedByUsername);
}
