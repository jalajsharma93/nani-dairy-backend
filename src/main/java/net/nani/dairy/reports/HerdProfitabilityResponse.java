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
public class HerdProfitabilityResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private int windowDays;

    private int totalAnimals;
    private int positiveAnimals;
    private int negativeAnimals;
    private int cullingReviewCount;

    private double totalEstimatedRevenue;
    private double totalEstimatedCost;
    private double totalEstimatedNet;
    private Double avgRoiPercent;

    private List<HerdProfitabilityItemResponse> items;
}
