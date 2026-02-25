package net.nani.dairy.animals.dto;

import lombok.*;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalResponse {
    private String animalId;
    private String tag;
    private String name;
    private String breed;
    private AnimalStatus status;
    private boolean isActive;
    private String motherAnimalId;
    private String sireTag;
    private LocalDate dateOfBirth;
    private AnimalGrowthStage growthStage;
    private Double birthWeightKg;
    private Double currentWeightKg;
    private LocalDate lastWeightDate;
    private LocalDate weaningDate;
    private Double weaningWeightKg;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
