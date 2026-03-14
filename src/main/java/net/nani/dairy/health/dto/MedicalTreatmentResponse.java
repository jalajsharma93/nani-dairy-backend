package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.nani.dairy.health.TreatmentComplianceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalTreatmentResponse {
    private String treatmentId;
    private String animalId;
    private LocalDate treatmentDate;
    private String templateCode;
    private String templateTitle;
    private String diagnosis;
    private String medicineName;
    private String dose;
    private String route;
    private String veterinarianName;
    private String prescriptionPhotoUrl;
    private String prescriptionIssuedBy;
    private LocalDate prescriptionIssuedDate;
    private String prescriptionReferenceNo;
    private LocalDate withdrawalTillDate;
    private LocalDate minimumWithdrawalTillDate;
    private Boolean prescriptionRequired;
    private TreatmentComplianceStatus complianceStatus;
    private String complianceMessage;
    private LocalDate followUpDate;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
