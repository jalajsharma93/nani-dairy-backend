package net.nani.dairy.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimalProfitabilityResponse {
    private String animalId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private int windowDays;

    private double avgMilkPrice;
    private double animalMilkLiters;
    private double totalMilkLiters;
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

    private double feedCostPerKg;
    private double treatmentCostPerCase;
    private double laborCostPerLiter;

    private String confidence; // HIGH | MEDIUM | LOW
    private boolean cullingReviewSuggested;
    private String recommendation;
    private List<String> warnings;
}
