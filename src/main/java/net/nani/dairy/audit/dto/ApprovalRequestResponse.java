package net.nani.dairy.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.audit.ApprovalStatus;
import net.nani.dairy.auth.UserRole;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequestResponse {
    private String approvalRequestId;
    private String module;
    private String actionType;
    private String targetRefId;
    private ApprovalStatus status;
    private UserRole requiredApproverRole;
    private String requestedByUsername;
    private UserRole requestedByRole;
    private String requestReason;
    private String requestPayloadJson;
    private String decisionNote;
    private String approvedByUsername;
    private UserRole approvedByRole;
    private OffsetDateTime decidedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
