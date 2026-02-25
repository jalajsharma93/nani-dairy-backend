package net.nani.dairy.animals;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "animal", uniqueConstraints = @UniqueConstraint(name = "uk_animal_tag", columnNames = {"tag"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalEntity {

    @Id
    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "tag", nullable = false, length = 80)
    private String tag;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "breed", nullable = false, length = 80)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AnimalStatus status;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "mother_animal_id", length = 80)
    private String motherAnimalId;

    @Column(name = "sire_tag", length = 80)
    private String sireTag;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "growth_stage", length = 20)
    private AnimalGrowthStage growthStage;

    @Column(name = "birth_weight_kg")
    private Double birthWeightKg;

    @Column(name = "current_weight_kg")
    private Double currentWeightKg;

    @Column(name = "last_weight_date")
    private LocalDate lastWeightDate;

    @Column(name = "weaning_date")
    private LocalDate weaningDate;

    @Column(name = "weaning_weight_kg")
    private Double weaningWeightKg;

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
