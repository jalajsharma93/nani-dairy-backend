package net.nani.dairy.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMedicalTreatmentRequest {

    @NotNull
    private LocalDate treatmentDate;

    @NotBlank
    private String diagnosis;

    @NotBlank
    private String medicineName;

    private String dose;

    private String route;

    private String veterinarianName;

    private String prescriptionPhotoUrl;

    private LocalDate withdrawalTillDate;

    private LocalDate followUpDate;

    private String notes;
}
