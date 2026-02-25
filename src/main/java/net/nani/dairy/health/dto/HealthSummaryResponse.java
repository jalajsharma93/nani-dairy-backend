package net.nani.dairy.health.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSummaryResponse {
    private LocalDate date;
    private Integer windowDays;
    private long vaccinationsDueToday;
    private long vaccinationsDueSoon;
    private long vaccinationsOverdue;
    private long dewormingDueToday;
    private long dewormingDueSoon;
    private long dewormingOverdue;
}
