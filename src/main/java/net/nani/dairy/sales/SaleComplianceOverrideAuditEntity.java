package net.nani.dairy.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sale_compliance_override_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleComplianceOverrideAuditEntity {

    @Id
    @Column(name = "sale_override_audit_id", nullable = false, length = 80)
    private String saleOverrideAuditId;

    @Column(name = "sale_id", nullable = false, length = 80)
    private String saleId;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Column(name = "batch_date", nullable = false)
    private LocalDate batchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_shift", nullable = false, length = 10)
    private Shift batchShift;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Column(name = "override_reason", nullable = false, length = 700)
    private String overrideReason;

    @Column(name = "blocked_animal_ids", nullable = false, length = 1200)
    private String blockedAnimalIds;

    @Column(name = "blocked_animal_tags", nullable = false, length = 1200)
    private String blockedAnimalTags;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
