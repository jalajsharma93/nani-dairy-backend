package net.nani.dairy.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentComplianceSummaryResponse {
    private LocalDate date;
    private String scopeAnimalId;
    private int evaluatedRecords;
    private int compliant;
    private int missingPrescription;
    private int missingPrescriptionMetadata;
    private int missingWithdrawalDate;
    private int withdrawalBelowMinimum;
    private int activeWithdrawal;
    private int followUpDueToday;
    private int followUpDueSoon;
    private int followUpOverdue;
}
