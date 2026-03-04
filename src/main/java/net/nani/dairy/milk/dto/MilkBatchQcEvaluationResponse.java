package net.nani.dairy.milk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.QcStatus;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilkBatchQcEvaluationResponse {

    private LocalDate date;
    private Shift shift;
    private QcStatus recommendedQcStatus;

    private int reviewedEntries;
    private int passEntries;
    private int holdEntries;
    private int rejectEntries;

    private int lowFatHoldCount;
    private int lowFatRejectCount;
    private int lowSnfHoldCount;
    private int lowSnfRejectCount;
    private int highTemperatureHoldCount;
    private int lactometerOutOfRangeHoldCount;
    private int badSmellHoldCount;
    private int explicitRejectCount;

    private List<String> triggerCodes;
}
