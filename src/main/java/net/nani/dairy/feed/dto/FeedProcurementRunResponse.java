package net.nani.dairy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProcurementRunResponse {
    private String feedProcurementRunId;
    private LocalDate planDate;
    private LocalDate taskDate;
    private String runMode;
    private int lookbackDays;
    private int horizonDays;
    private int consideredItems;
    private int createdTasks;
    private int skippedTasks;
    private String actor;
    private String notes;
    private OffsetDateTime createdAt;
}
