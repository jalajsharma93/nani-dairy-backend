package net.nani.dairy.health.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaccinationResponse {
    private String vaccinationId;
    private String animalId;
    private String vaccineName;
    private String diseaseTarget;
    private LocalDate doseDate;
    private Integer doseNumber;
    private LocalDate boosterDueDate;
    private LocalDate nextDueDate;
    private LocalDate vaccineExpiryDate;
    private String batchLotNo;
    private String route;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
