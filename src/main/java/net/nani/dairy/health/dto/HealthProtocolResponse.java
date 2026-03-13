package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalStatus;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthProtocolResponse {
    private LocalDate date;
    private Integer windowDays;
    private String animalId;
    private String animalTag;
    private AnimalStatus animalStatus;
    private AnimalGrowthStage growthStage;
    private Long ageDays;
    private Integer ageMonths;
    private Integer totalItems;
    private Long highPriorityCount;
    private Long mediumPriorityCount;
    private Long lowPriorityCount;
    private List<HealthProtocolItemResponse> items;
}
