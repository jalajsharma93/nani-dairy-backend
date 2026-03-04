package net.nani.dairy.milk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "milk_qc_override_audit", indexes = {
        @Index(name = "idx_milk_qc_override_audit_date_shift", columnList = "batch_date,shift"),
        @Index(name = "idx_milk_qc_override_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkQcOverrideAuditEntity {

    @Id
    @Column(name = "milk_qc_override_audit_id", nullable = false, length = 80)
    private String milkQcOverrideAuditId;

    @Column(name = "batch_date", nullable = false)
    private LocalDate batchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 10)
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_qc_status", nullable = false, length = 20)
    private QcStatus requestedQcStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_qc_status", nullable = false, length = 20)
    private QcStatus recommendedQcStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_qc_status", nullable = false, length = 20)
    private QcStatus appliedQcStatus;

    @Column(name = "override_requested", nullable = false)
    private boolean overrideRequested;

    @Column(name = "override_approved", nullable = false)
    private boolean overrideApproved;

    @Column(name = "override_reason", length = 700)
    private String overrideReason;

    @Column(name = "trigger_codes_csv", length = 700)
    private String triggerCodesCsv;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
