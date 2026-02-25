package net.nani.dairy.health.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DewormingResponse {
    private String dewormingId;
    private String animalId;
    private String drugName;
    private LocalDate doseDate;
    private LocalDate nextDueDate;
    private Double weightAtDoseKg;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
