package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.feed.FeedRationPhase;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedEfficiencyPhaseSummaryResponse {
    private FeedRationPhase rationPhase;
    private int animals;
    private double totalFeedKg;
    private double totalMilkLiters;
    private Double feedPerLiter;
    private Double milkPerKgFeed;
    private String recommendation;
}
