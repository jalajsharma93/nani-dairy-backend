package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.animals.AnimalGrowthStage;
import net.nani.dairy.animals.AnimalStatus;
import net.nani.dairy.feed.FeedRationPhase;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedEfficiencyAnimalItemResponse {
    private String animalId;
    private String tag;
    private String name;
    private AnimalStatus status;
    private AnimalGrowthStage growthStage;
    private FeedRationPhase dominantRationPhase;

    private int feedLogDays;
    private int milkLogDays;
    private double totalFeedKg;
    private double totalMilkLiters;
    private double avgFeedPerFeedDayKg;
    private double avgMilkPerMilkDayLiters;
    private Double feedPerLiter;
    private Double milkPerKgFeed;
    private Double recent7FeedPerLiter;
    private Double prior7FeedPerLiter;
    private String trend; // IMPROVING | DECLINING | STABLE | INSUFFICIENT_DATA

    private String efficiencyBand; // EFFICIENT | WATCH | INEFFICIENT | DATA_GAP
    private String anomalyCode; // HIGH_FEED_PER_LITER | NO_MILK_WITH_FEED | MISSING_FEED_LOGS | ...
    private String recommendation;
}
