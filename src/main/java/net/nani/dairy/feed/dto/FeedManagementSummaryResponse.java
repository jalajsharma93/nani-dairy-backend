package net.nani.dairy.feed.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedManagementSummaryResponse {
    private LocalDate date;
    private int totalMaterials;
    private int lowStockMaterials;
    private int activeRecipes;
    private int openTasks;
    private int doneTasksToday;
    private double totalInventoryValue;
}
