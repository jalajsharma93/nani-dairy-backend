package net.nani.dairy.health.dto;

import lombok.*;
import net.nani.dairy.health.BreedingCalfGender;
import net.nani.dairy.health.BreedingCalvingOutcome;
import net.nani.dairy.health.BreedingPregnancyResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreedingEventResponse {
    private String breedingEventId;
    private String animalId;
    private LocalDate heatDate;
    private LocalDate inseminationDate;
    private String sireTag;
    private LocalDate pregnancyCheckDate;
    private BreedingPregnancyResult pregnancyResult;
    private LocalDate expectedCalvingDate;
    private LocalDate actualCalvingDate;
    private String calfAnimalId;
    private String calfTag;
    private BreedingCalfGender calfGender;
    private BreedingCalvingOutcome calvingOutcome;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
