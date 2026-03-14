package net.nani.dairy.animals;

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

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "animal_lifecycle_event",
        indexes = {
                @Index(name = "idx_animal_lifecycle_event_animal", columnList = "animal_id"),
                @Index(name = "idx_animal_lifecycle_event_changed_at", columnList = "changed_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalLifecycleEventEntity {

    @Id
    @Column(name = "animal_lifecycle_event_id", nullable = false, length = 40)
    private String animalLifecycleEventId;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private AnimalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private AnimalStatus toStatus;

    @Column(name = "from_active")
    private Boolean fromActive;

    @Column(name = "to_active", nullable = false)
    private boolean toActive;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "changed_by", length = 120)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @PrePersist
    public void prePersist() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now();
        }
    }
}
