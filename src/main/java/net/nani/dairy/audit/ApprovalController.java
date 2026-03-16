package net.nani.dairy.audit;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.audit.dto.ApprovalRequestDecisionRequest;
import net.nani.dairy.audit.dto.ApprovalRequestResponse;
import net.nani.dairy.audit.dto.AuditEventResponse;
import net.nani.dairy.audit.dto.CreateApprovalRequest;
import net.nani.dairy.auth.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/governance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/approvals/request")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public ApprovalRequestResponse requestApproval(
            @Valid @RequestBody CreateApprovalRequest req,
            Authentication authentication
    ) {
        return approvalService.requestApproval(req, actor(authentication), actorRole(authentication));
    }

    @PostMapping("/approvals/{approvalRequestId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApprovalRequestResponse approve(
            @PathVariable String approvalRequestId,
            @Valid @RequestBody(required = false) ApprovalRequestDecisionRequest req,
            Authentication authentication
    ) {
        ApprovalRequestDecisionRequest safeReq = req != null ? req : ApprovalRequestDecisionRequest.builder().build();
        return approvalService.approve(approvalRequestId, safeReq, actor(authentication), actorRole(authentication));
    }

    @PostMapping("/approvals/{approvalRequestId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApprovalRequestResponse reject(
            @PathVariable String approvalRequestId,
            @Valid @RequestBody(required = false) ApprovalRequestDecisionRequest req,
            Authentication authentication
    ) {
        ApprovalRequestDecisionRequest safeReq = req != null ? req : ApprovalRequestDecisionRequest.builder().build();
        return approvalService.reject(approvalRequestId, safeReq, actor(authentication), actorRole(authentication));
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public List<ApprovalRequestResponse> approvals(
            @RequestParam(required = false) ApprovalStatus status,
            Authentication authentication
    ) {
        UserRole actorRole = actorRole(authentication);
        boolean privilegedActor = actorRole == UserRole.ADMIN || actorRole == UserRole.MANAGER;
        return approvalService.listApprovals(status, actor(authentication), actorRole, privilegedActor);
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<AuditEventResponse> audits(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String targetRefId,
            @RequestParam(required = false) Integer limit
    ) {
        return approvalService.listAudits(module, targetRefId, limit);
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return authentication.getName();
    }

    private UserRole actorRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return UserRole.WORKER;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                try {
                    return UserRole.valueOf(value.substring("ROLE_".length()).toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // no-op
                }
            }
        }
        return UserRole.WORKER;
    }
}
