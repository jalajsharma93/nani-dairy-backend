package net.nani.dairy.health;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "animal_vaccination", indexes = {
        @Index(name = "idx_vaccination_animal", columnList = "animal_id"),
        @Index(name = "idx_vaccination_next_due", columnList = "next_due_date"),
        @Index(name = "idx_vaccination_dose_date", columnList = "dose_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaccinationEntity {

    @Id
    @Column(name = "vaccination_id", nullable = false, length = 100)
    private String vaccinationId;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "vaccine_name", nullable = false, length = 120)
    private String vaccineName;

    @Column(name = "disease_target", nullable = false, length = 120)
    private String diseaseTarget;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "booster_due_date")
    private LocalDate boosterDueDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "vaccine_expiry_date")
    private LocalDate vaccineExpiryDate;

    @Column(name = "batch_lot_no", length = 80)
    private String batchLotNo;

    @Column(name = "admin_route", length = 40)
    private String route;

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
