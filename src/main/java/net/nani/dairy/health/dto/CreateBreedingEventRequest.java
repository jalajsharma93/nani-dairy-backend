package net.nani.dairy.health.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.nani.dairy.health.BreedingCalfGender;
import net.nani.dairy.health.BreedingCalvingOutcome;
import net.nani.dairy.health.BreedingPregnancyResult;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBreedingEventRequest {

    @NotNull
    private LocalDate heatDate;

    private LocalDate inseminationDate;

    @Size(max = 80)
    private String sireTag;

    private LocalDate pregnancyCheckDate;

    private BreedingPregnancyResult pregnancyResult;

    private LocalDate expectedCalvingDate;

    private LocalDate actualCalvingDate;

    @Size(max = 80)
    private String calfAnimalId;

    @Size(max = 80)
    private String calfTag;

    private BreedingCalfGender calfGender;

    private BreedingCalvingOutcome calvingOutcome;

    @Size(max = 700)
    private String notes;
}
