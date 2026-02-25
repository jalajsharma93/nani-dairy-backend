package net.nani.dairy.health;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "animal_deworming", indexes = {
        @Index(name = "idx_deworming_animal", columnList = "animal_id"),
        @Index(name = "idx_deworming_next_due", columnList = "next_due_date"),
        @Index(name = "idx_deworming_dose_date", columnList = "dose_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DewormingEntity {

    @Id
    @Column(name = "deworming_id", nullable = false, length = 100)
    private String dewormingId;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "drug_name", nullable = false, length = 120)
    private String drugName;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "weight_at_dose_kg")
    private Double weightAtDoseKg;

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
