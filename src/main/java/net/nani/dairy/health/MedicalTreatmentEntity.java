package net.nani.dairy.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "animal_treatment", indexes = {
        @Index(name = "idx_treatment_animal", columnList = "animal_id"),
        @Index(name = "idx_treatment_date", columnList = "treatment_date"),
        @Index(name = "idx_treatment_follow_up", columnList = "follow_up_date"),
        @Index(name = "idx_treatment_withdrawal", columnList = "withdrawal_till_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalTreatmentEntity {

    @Id
    @Column(name = "treatment_id", nullable = false, length = 100)
    private String treatmentId;

    @Column(name = "animal_id", nullable = false, length = 80)
    private String animalId;

    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    @Column(name = "template_code", length = 80)
    private String templateCode;

    @Column(name = "diagnosis", nullable = false, length = 160)
    private String diagnosis;

    @Column(name = "medicine_name", nullable = false, length = 160)
    private String medicineName;

    @Column(name = "dose", length = 100)
    private String dose;

    @Column(name = "route", length = 60)
    private String route;

    @Column(name = "veterinarian_name", length = 120)
    private String veterinarianName;

    @Column(name = "prescription_photo_url", length = 700)
    private String prescriptionPhotoUrl;

    @Column(name = "prescription_issued_by", length = 120)
    private String prescriptionIssuedBy;

    @Column(name = "prescription_issued_date")
    private LocalDate prescriptionIssuedDate;

    @Column(name = "prescription_reference_no", length = 80)
    private String prescriptionReferenceNo;

    @Column(name = "withdrawal_till_date")
    private LocalDate withdrawalTillDate;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

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
