package net.nani.dairy.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserAuditResponse {
    private String auditId;
    private String actorUsername;
    private String action;
    private String targetUserId;
    private String targetUsername;
    private String details;
    private OffsetDateTime createdAt;
}
