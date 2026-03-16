package net.nani.dairy.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "audit_event",
        indexes = {
                @Index(name = "idx_audit_event_module", columnList = "module"),
                @Index(name = "idx_audit_event_action", columnList = "action_type"),
                @Index(name = "idx_audit_event_target", columnList = "target_ref_id"),
                @Index(name = "idx_audit_event_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventEntity {

    @Id
    @Column(name = "audit_event_id", length = 80, nullable = false)
    private String auditEventId;

    @Column(name = "module", length = 60, nullable = false)
    private String module;

    @Column(name = "action_type", length = 100, nullable = false)
    private String actionType;

    @Column(name = "target_ref_id", length = 120)
    private String targetRefId;

    @Column(name = "actor_username", length = 120, nullable = false)
    private String actorUsername;

    @Column(name = "actor_role", length = 40, nullable = false)
    private String actorRole;

    @Column(name = "payload_json", length = 8000)
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
