package net.nani.dairy.sales.dto;

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
public class SubscriptionGenerationPreviewResponse {
    private LocalDate date;
    private int totalCandidates;
    private int eligibleCandidates;
    private int skippedCandidates;
    private List<SubscriptionGenerationPreviewItemResponse> items;
}
