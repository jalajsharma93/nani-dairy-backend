package net.nani.dairy.audit.dto;

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
public class AuditEventResponse {
    private String auditEventId;
    private String module;
    private String actionType;
    private String targetRefId;
    private String actorUsername;
    private String actorRole;
    private String payloadJson;
    private OffsetDateTime createdAt;
}
