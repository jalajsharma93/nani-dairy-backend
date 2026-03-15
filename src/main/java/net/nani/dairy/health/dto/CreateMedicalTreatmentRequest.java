package net.nani.dairy.health.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMedicalTreatmentRequest {

    @NotNull
    private LocalDate treatmentDate;

    private String templateCode;

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

    private LocalDate followUpDate;

    private String notes;

    private OffsetDateTime expectedUpdatedAt;
}
