package net.nani.dairy.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.audit.dto.ApprovalRequestDecisionRequest;
import net.nani.dairy.audit.dto.ApprovalRequestResponse;
import net.nani.dairy.audit.dto.AuditEventResponse;
import net.nani.dairy.audit.dto.CreateApprovalRequest;
import net.nani.dairy.auth.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final AuditEventRepository auditEventRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApprovalRequestResponse requestApproval(
            CreateApprovalRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        String actor = normalizeRequired(actorUsername, "Unauthorized");
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;

        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .approvalRequestId(buildApprovalRequestId())
                .module(normalizeUpper(req.getModule(), "module is required"))
                .actionType(normalizeUpper(req.getActionType(), "actionType is required"))
                .targetRefId(trimToNull(req.getTargetRefId()))
                .status(ApprovalStatus.PENDING)
                .requiredApproverRole(req.getRequiredApproverRole() != null ? req.getRequiredApproverRole() : UserRole.ADMIN)
                .requestedByUsername(actor)
                .requestedByRole(safeActorRole)
                .requestReason(normalizeRequired(req.getRequestReason(), "requestReason is required"))
                .requestPayloadJson(trimToNull(req.getRequestPayloadJson()))
                .build();

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        logAudit(
                saved.getModule(),
                "APPROVAL_REQUEST_CREATED",
                saved.getApprovalRequestId(),
                actor,
                safeActorRole,
                Map.of(
                        "actionType", saved.getActionType(),
                        "targetRefId", safeValue(saved.getTargetRefId()),
                        "requiredApproverRole", saved.getRequiredApproverRole().name()
                )
        );
        return toResponse(saved);
    }

    public List<ApprovalRequestResponse> listApprovals(
            ApprovalStatus status,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        String actor = normalizeRequired(actorUsername, "Unauthorized");

        List<ApprovalRequestEntity> rows;
        if (status != null) {
            rows = approvalRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            rows = approvalRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        if (!privilegedActor) {
            rows = rows.stream()
                    .filter(row -> actor.equalsIgnoreCase(trimToNull(row.getRequestedByUsername())))
                    .toList();
        }

        return rows.stream().map(this::toResponse).toList();
    }

    public List<AuditEventResponse> listAudits(String module, String targetRefId, Integer limit) {
        int safeLimit = limit == null ? 300 : Math.max(1, Math.min(limit, 500));
        String normalizedModule = trimToNull(module);
        String normalizedTarget = trimToNull(targetRefId);

        List<AuditEventEntity> rows;
        if (normalizedTarget != null) {
            rows = auditEventRepository.findByTargetRefIdIgnoreCaseOrderByCreatedAtDesc(normalizedTarget);
        } else if (normalizedModule != null) {
            rows = auditEventRepository.findByModuleIgnoreCaseOrderByCreatedAtDesc(normalizedModule);
        } else {
            rows = auditEventRepository.findTop500ByOrderByCreatedAtDesc();
        }

        return rows.stream().limit(safeLimit).map(this::toAuditResponse).toList();
    }

    @Transactional
    public ApprovalRequestResponse approve(
            String approvalRequestId,
            ApprovalRequestDecisionRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        ApprovalRequestEntity entity = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        String actor = normalizeRequired(actorUsername, "Unauthorized");

        if (entity.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Approval request is already decided");
        }
        if (!canDecide(entity.getRequiredApproverRole(), safeActorRole)) {
            throw new AccessDeniedException("Your role cannot approve this request");
        }
        if (!safeActorRole.equals(UserRole.ADMIN) && actor.equalsIgnoreCase(trimToNull(entity.getRequestedByUsername()))) {
            throw new AccessDeniedException("Requester cannot self-approve");
        }

        entity.setStatus(ApprovalStatus.APPROVED);
        entity.setDecisionNote(trimToNull(req.getDecisionNote()));
        entity.setApprovedByUsername(actor);
        entity.setApprovedByRole(safeActorRole);
        entity.setDecidedAt(OffsetDateTime.now());

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        logAudit(
                saved.getModule(),
                "APPROVAL_REQUEST_APPROVED",
                saved.getApprovalRequestId(),
                actor,
                safeActorRole,
                Map.of("actionType", saved.getActionType(), "targetRefId", safeValue(saved.getTargetRefId()))
        );

        return toResponse(saved);
    }

    @Transactional
    public ApprovalRequestResponse reject(
            String approvalRequestId,
            ApprovalRequestDecisionRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        ApprovalRequestEntity entity = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        String actor = normalizeRequired(actorUsername, "Unauthorized");

        if (entity.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Approval request is already decided");
        }
        if (!canDecide(entity.getRequiredApproverRole(), safeActorRole)) {
            throw new AccessDeniedException("Your role cannot reject this request");
        }

        entity.setStatus(ApprovalStatus.REJECTED);
        entity.setDecisionNote(trimToNull(req.getDecisionNote()));
        entity.setApprovedByUsername(actor);
        entity.setApprovedByRole(safeActorRole);
        entity.setDecidedAt(OffsetDateTime.now());

        ApprovalRequestEntity saved = approvalRequestRepository.save(entity);
        logAudit(
                saved.getModule(),
                "APPROVAL_REQUEST_REJECTED",
                saved.getApprovalRequestId(),
                actor,
                safeActorRole,
                Map.of("actionType", saved.getActionType(), "targetRefId", safeValue(saved.getTargetRefId()))
        );
        return toResponse(saved);
    }

    public void assertApprovedForAction(
            String approvalRequestId,
            String module,
            String actionType,
            String actorUsername,
            UserRole actorRole,
            UserRole minimumApproverRole
    ) {
        String normalizedId = trimToNull(approvalRequestId);
        if (normalizedId == null) {
            throw new IllegalArgumentException("approvalRequestId is required for this action");
        }
        ApprovalRequestEntity approval = approvalRequestRepository.findById(normalizedId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

        String normalizedModule = normalizeUpper(module, "module is required");
        String normalizedActionType = normalizeUpper(actionType, "actionType is required");
        if (!normalizedModule.equalsIgnoreCase(approval.getModule())) {
            throw new IllegalArgumentException("Approval request module mismatch");
        }
        if (!normalizedActionType.equalsIgnoreCase(approval.getActionType())) {
            throw new IllegalArgumentException("Approval request actionType mismatch");
        }
        if (approval.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Approval request is not approved");
        }

        UserRole requiredApprover = minimumApproverRole != null ? minimumApproverRole : UserRole.ADMIN;
        UserRole decidedByRole = approval.getApprovedByRole() != null ? approval.getApprovedByRole() : UserRole.WORKER;
        if (roleRank(decidedByRole) < roleRank(requiredApprover)) {
            throw new IllegalArgumentException("Approval request does not satisfy required approver role");
        }

        String actor = normalizeRequired(actorUsername, "Unauthorized");
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        if (!safeActorRole.equals(UserRole.ADMIN)
                && !actor.equalsIgnoreCase(trimToNull(approval.getRequestedByUsername()))) {
            throw new AccessDeniedException("Approval request was not created for this actor");
        }
    }

    @Transactional
    public void logAudit(
            String module,
            String actionType,
            String targetRefId,
            String actorUsername,
            UserRole actorRole,
            Object payload
    ) {
        String normalizedModule = normalizeUpper(module, "module is required");
        String normalizedActionType = normalizeUpper(actionType, "actionType is required");
        String actor = normalizeRequired(actorUsername, "actorUsername is required");
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;

        AuditEventEntity event = AuditEventEntity.builder()
                .auditEventId(buildAuditEventId())
                .module(normalizedModule)
                .actionType(normalizedActionType)
                .targetRefId(trimToNull(targetRefId))
                .actorUsername(actor)
                .actorRole(safeActorRole.name())
                .payloadJson(payloadToJson(payload))
                .createdAt(OffsetDateTime.now())
                .build();
        auditEventRepository.save(event);
    }

    private boolean canDecide(UserRole requiredApproverRole, UserRole actorRole) {
        UserRole required = requiredApproverRole != null ? requiredApproverRole : UserRole.ADMIN;
        UserRole actor = actorRole != null ? actorRole : UserRole.WORKER;
        return roleRank(actor) >= roleRank(required);
    }

    private int roleRank(UserRole role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case ADMIN -> 30;
            case MANAGER -> 20;
            case VET, FEED_MANAGER, DELIVERY -> 15;
            case WORKER -> 10;
        };
    }

    private ApprovalRequestResponse toResponse(ApprovalRequestEntity row) {
        return ApprovalRequestResponse.builder()
                .approvalRequestId(row.getApprovalRequestId())
                .module(row.getModule())
                .actionType(row.getActionType())
                .targetRefId(row.getTargetRefId())
                .status(row.getStatus())
                .requiredApproverRole(row.getRequiredApproverRole())
                .requestedByUsername(row.getRequestedByUsername())
                .requestedByRole(row.getRequestedByRole())
                .requestReason(row.getRequestReason())
                .requestPayloadJson(row.getRequestPayloadJson())
                .decisionNote(row.getDecisionNote())
                .approvedByUsername(row.getApprovedByUsername())
                .approvedByRole(row.getApprovedByRole())
                .decidedAt(row.getDecidedAt())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private AuditEventResponse toAuditResponse(AuditEventEntity row) {
        return AuditEventResponse.builder()
                .auditEventId(row.getAuditEventId())
                .module(row.getModule())
                .actionType(row.getActionType())
                .targetRefId(row.getTargetRefId())
                .actorUsername(row.getActorUsername())
                .actorRole(row.getActorRole())
                .payloadJson(row.getPayloadJson())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private String payloadToJson(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String str) {
            return trimToNull(str);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ignored) {
            return payload.toString();
        }
    }

    private String buildApprovalRequestId() {
        return "APR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String buildAuditEventId() {
        return "AUD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeUpper(String value, String message) {
        String normalized = normalizeRequired(value, message);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
