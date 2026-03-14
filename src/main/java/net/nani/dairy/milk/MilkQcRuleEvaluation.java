package net.nani.dairy.milk;

import java.util.List;

public record MilkQcRuleEvaluation(
        QcStatus recommendedQcStatus,
        int reviewedEntries,
        int passEntries,
        int holdEntries,
        int rejectEntries,
        int lowFatHoldCount,
        int lowFatRejectCount,
        int lowSnfHoldCount,
        int lowSnfRejectCount,
        int highTemperatureHoldCount,
        int lactometerOutOfRangeHoldCount,
        int badSmellHoldCount,
        int abnormalColorHoldCount,
        int highAcidityHoldCount,
        int waterAdulterationRejectCount,
        int antibioticResidueRejectCount,
        int highBacterialCountHoldCount,
        int explicitRejectCount,
        List<String> triggerCodes
) {
}
