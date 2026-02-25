package net.nani.dairy.animals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalStatus;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAnimalRequest {

    @NotBlank
    private String tag;

    @Size(max = 120)
    private String name;

    @NotBlank
    private String breed;

    @NotNull
    private AnimalStatus status;

    @NotNull
    private Boolean isActive;

    @Size(max = 80)
    private String motherAnimalId;

    @Size(max = 80)
    private String sireTag;

    private LocalDate dateOfBirth;

    private AnimalGrowthStage growthStage;

    @PositiveOrZero
    private Double birthWeightKg;

    @PositiveOrZero
    private Double currentWeightKg;

    private LocalDate lastWeightDate;

    private LocalDate weaningDate;

    @PositiveOrZero
    private Double weaningWeightKg;
}
