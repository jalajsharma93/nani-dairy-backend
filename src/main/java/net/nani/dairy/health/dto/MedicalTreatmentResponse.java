package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String diagnosis;
    private String medicineName;
    private String dose;
    private String route;
    private String veterinarianName;
    private String prescriptionPhotoUrl;
    private LocalDate withdrawalTillDate;
    private LocalDate followUpDate;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
