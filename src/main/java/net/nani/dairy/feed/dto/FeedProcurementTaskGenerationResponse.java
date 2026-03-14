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
public class FeedProcurementTaskGenerationResponse {
    private String feedProcurementRunId;
    private String runMode;
    private LocalDate date;
    private LocalDate taskDate;
    private int lookbackDays;
    private int horizonDays;

    private int consideredItems;
    private int createdTasks;
    private int skippedTasks;

    private List<String> createdTaskIds;
    private List<String> skippedTaskTitles;
}
