package net.nani.dairy.feed.dto;

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
public class FeedEfficiencyInsightResponse {
    private LocalDate date;
    private int lookbackDays;
    private LocalDate fromDate;
    private int animalsTracked;
    private int animalsWithBothFeedAndMilk;
    private int efficientAnimals;
    private int watchAnimals;
    private int inefficientAnimals;
    private int dataGapAnimals;

    private double totalFeedKg;
    private double totalMilkLiters;
    private Double herdFeedPerLiter;
    private Double herdMilkPerKgFeed;
    private Double herdMedianFeedPerLiter;

    private Double recent7HerdFeedPerLiter;
    private Double prior7HerdFeedPerLiter;
    private String herdTrend; // IMPROVING | DECLINING | STABLE | INSUFFICIENT_DATA

    private Double avgFeedCostPerKg;
    private double potentialFeedSavingsKg30Days;
    private double potentialFeedCostSavings30Days;

    private List<FeedEfficiencyPhaseSummaryResponse> phaseSummaries;
    private List<FeedEfficiencyAnimalItemResponse> items;
}
