package net.nani.dairy.feed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "feed_log", indexes = {
        @Index(name = "idx_feed_log_date", columnList = "feed_date"),
        @Index(name = "idx_feed_log_animal", columnList = "animal_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedLogEntity {

    @Id
    @Column(name = "feed_log_id", nullable = false, length = 100)
    private String feedLogId;

    @Column(name = "feed_date", nullable = false)
    private LocalDate feedDate;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "feed_type", nullable = false, length = 100)
    private String feedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ration_phase", length = 30)
    private FeedRationPhase rationPhase;

    @Column(name = "quantity_kg", nullable = false)
    private double quantityKg;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
