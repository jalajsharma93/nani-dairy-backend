package net.nani.dairy.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.auth.UserRole;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "approval_request",
        indexes = {
                @Index(name = "idx_approval_request_module", columnList = "module"),
                @Index(name = "idx_approval_request_action", columnList = "action_type"),
                @Index(name = "idx_approval_request_status", columnList = "status"),
                @Index(name = "idx_approval_request_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequestEntity {

    @Id
    @Column(name = "approval_request_id", length = 80, nullable = false)
    private String approvalRequestId;

    @Column(name = "module", length = 60, nullable = false)
    private String module;

    @Column(name = "action_type", length = 100, nullable = false)
    private String actionType;

    @Column(name = "target_ref_id", length = 120)
    private String targetRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApprovalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_approver_role", length = 40, nullable = false)
    private UserRole requiredApproverRole;

    @Column(name = "requested_by_username", length = 120, nullable = false)
    private String requestedByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_role", length = 40, nullable = false)
    private UserRole requestedByRole;

    @Column(name = "request_reason", length = 700, nullable = false)
    private String requestReason;

    @Column(name = "request_payload_json", length = 8000)
    private String requestPayloadJson;

    @Column(name = "decision_note", length = 700)
    private String decisionNote;

    @Column(name = "approved_by_username", length = 120)
    private String approvedByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "approved_by_role", length = 40)
    private UserRole approvedByRole;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ApprovalStatus.PENDING;
        }
        if (requiredApproverRole == null) {
            requiredApproverRole = UserRole.ADMIN;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
