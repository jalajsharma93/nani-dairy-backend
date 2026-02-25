package net.nani.dairy.health;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "breeding_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreedingEventEntity {

    @Id
    @Column(name = "breeding_event_id", nullable = false, length = 80)
    private String breedingEventId;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "heat_date", nullable = false)
    private LocalDate heatDate;

    @Column(name = "insemination_date")
    private LocalDate inseminationDate;

    @Column(name = "sire_tag", length = 80)
    private String sireTag;

    @Column(name = "pregnancy_check_date")
    private LocalDate pregnancyCheckDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pregnancy_result", nullable = false, length = 30)
    private BreedingPregnancyResult pregnancyResult;

    @Column(name = "expected_calving_date")
    private LocalDate expectedCalvingDate;

    @Column(name = "actual_calving_date")
    private LocalDate actualCalvingDate;

    @Column(name = "calf_animal_id", length = 80)
    private String calfAnimalId;

    @Column(name = "calf_tag", length = 80)
    private String calfTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "calf_gender", nullable = false, length = 20)
    private BreedingCalfGender calfGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "calving_outcome", nullable = false, length = 30)
    private BreedingCalvingOutcome calvingOutcome;

    @Column(name = "notes", length = 700)
    private String notes;

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
