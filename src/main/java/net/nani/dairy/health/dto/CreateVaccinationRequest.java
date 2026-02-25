package net.nani.dairy.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVaccinationRequest {

    @NotBlank
    private String vaccineName;

    @NotBlank
    private String diseaseTarget;

    @NotNull
    private LocalDate doseDate;

    private Integer doseNumber;

    private LocalDate boosterDueDate;

    private LocalDate nextDueDate;

    private LocalDate vaccineExpiryDate;

    private String batchLotNo;

    private String route;

    private String notes;
}
