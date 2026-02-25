package net.nani.dairy.health.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreedingSummaryResponse {
    private LocalDate date;
    private Integer windowDays;
    private Long calvingDueToday;
    private Long calvingDueSoon;
    private Long calvingOverdue;
    private Long openPregnancies;
}
