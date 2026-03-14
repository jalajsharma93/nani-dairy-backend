package net.nani.dairy.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.animals.AnimalStatus;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HerdProfitabilityItemResponse {
    private String animalId;
    private String tag;
    private String name;
    private String breed;
    private AnimalStatus status;
    private boolean active;

    private double animalMilkLiters;
    private double avgMilkPerDay;
    private double animalFeedKg;
    private int animalTreatmentCount;

    private double estimatedRevenue;
    private double estimatedFeedCost;
    private double estimatedTreatmentCost;
    private double estimatedLaborCost;
    private double estimatedTotalCost;
    private double estimatedNet;
    private Double roiPercent;

    private String confidence;
    private boolean cullingReviewSuggested;
    private String recommendation;
    private List<String> warnings;
}
