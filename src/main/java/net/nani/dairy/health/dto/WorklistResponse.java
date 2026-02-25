package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorklistResponse {
    private LocalDate date;
    private Integer windowDays;
    private OffsetDateTime generatedAt;
    private Integer totalTasks;
    private Long highPriorityCount;
    private Long mediumPriorityCount;
    private Long lowPriorityCount;
    private Long overdueCount;
    private Long dueTodayCount;
    private Long dueSoonCount;
    private List<WorklistItemResponse> items;
}
