package net.nani.dairy.milk;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "milk_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_milk_entry_date_shift_animal", columnNames = {"entry_date", "shift", "animal_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkEntryEntity {

    @Id
    @Column(name = "milk_entry_id", nullable = false, length = 100)
    private String milkEntryId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 10)
    private Shift shift;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "liters", nullable = false)
    private double liters;

    @Enumerated(EnumType.STRING)
    @Column(name = "qc_status", nullable = false, length = 20)
    private QcStatus qcStatus;

    @Column(name = "fat")
    private Double fat;

    @Column(name = "snf")
    private Double snf;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "lactometer")
    private Double lactometer;

    @Column(name = "smell_notes", length = 500)
    private String smellNotes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "color_observation", length = 40)
    private String colorObservation;

    @Column(name = "acidity")
    private Double acidity;

    @Column(name = "water_adulteration")
    private Boolean waterAdulteration;

    @Column(name = "antibiotic_residue")
    private Boolean antibioticResidue;

    @Column(name = "bacterial_count")
    private Double bacterialCount;

    @Column(name = "lab_test_attachment_url", length = 700)
    private String labTestAttachmentUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (qcStatus == null) qcStatus = QcStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
